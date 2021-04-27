/**
 * Set a cookie
 * @param name The name of the cookie
 * @param value The value of the cookie
 * @param ttl The TTL of the cookie in seconds
 */
export function setCookie(name: string, value: string, ttl: number): void {
    let date = new Date();
    date.setTime(date.getTime() + (ttl * 1000));
    let expires = "expires=" + date.toUTCString();

    document.cookie = name + "=" + value + ";" + expires + ";path=/";
}

/**
 * Get the value of a cookie
 * @param name the name of the cookie
 * @returns Retuns the value of the cookie, or '' if the cookie was not found
 */
export function getCookie(name: string): string {
    let re = new RegExp('[; ]'+name+'=([^\\s;]*)');
    let sMatch = (' '+document.cookie).match(re);
    if (name && sMatch) return unescape(sMatch[1]);
    return '';
}

/**
 * Get the value of a GET parameter
 * @param parameterName The name of the parameter
 * @returns The value of the requested parameter. Null if parameter doesn't exist
 */
export function findGetParameter(parameterName: string): string {
    let result = null,
        tmp = [];
    location.search
        .substr(1)
        .split("&")
        .forEach((item) => {
            tmp = item.split("=");
            if (tmp[0] === parameterName) result = decodeURIComponent(tmp[1]);
        });
    return result;
}
