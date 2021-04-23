use actix_web::{HttpResponse, web, post};
use serde::{Serialize, Deserialize};
use crate::jni::logging::{LogLevel, ConsoleLogItem};
use crate::endpoints::console::CombinedLogEntry;
use crate::database::Database;
use std::path::PathBuf;
use rusqlite::named_params;
use crate::jni::JvmCommand;

#[derive(Deserialize)]
pub struct LogRequest {
    session_id:      String
}

#[derive(Serialize)]
pub struct LogResponse {
    status:     i16,
    logs:       Option<Vec<CombinedLogEntry>>
}

#[post("/console/all")]
pub async fn post_logs_all(data: web::Data<crate::webserver::AppData>, form: web::Form<LogRequest>) -> HttpResponse {
    let db = Database::new(PathBuf::from(data.db_path.clone())).unwrap();
    let sql_check_session: rusqlite::Result<bool> =  db.connection.query_row("SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id = :session_id)", named_params! {
        ":session_id": &form.session_id
    }, |row| row.get(0));

    if sql_check_session.is_err() {
        let tx = data.jvm_command_tx.lock().unwrap();
        let jvm_command = JvmCommand::log(ConsoleLogItem::new(LogLevel::Warn,format!("An error occurred while verifying a session_id: {:?}", sql_check_session.err().unwrap()) ));
        tx.send(jvm_command).expect("An issue occurred while sending a JvmCommand");

        return HttpResponse::InternalServerError().finish();
    }

    if !sql_check_session.unwrap() {
        return HttpResponse::Ok().json(LogResponse { status: 401, logs: None});
    }

    //Iterate over the LOG_BUFFER map to construct a Vec<CombinedLogEntry>
    let mut combined_entries: Vec<CombinedLogEntry> = vec![];
    let buffer_pinned = crate::LOG_BUFFER.pin();
    for i in 0..(buffer_pinned.len() -1){
        let (k, v) = buffer_pinned.get_key_value(&(i as u32)).unwrap();
        combined_entries.push(CombinedLogEntry { id: *k, log_entry: v.clone() })
    }

    //Return the results
    return HttpResponse::Ok().json(LogResponse { status: 200, logs: Some(combined_entries) })
}