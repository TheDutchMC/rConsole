.PHONY: testjar releasejar ghactions clean
all: releasejar

RUST_SOURCE_FILES := $(shell find librconsole/src -type f)

librconsole/target/x86_64-unknown-linux-gnu/release/librconsole.so: ${RUST_SOURCE_FILES}
	cd librconsole; \
		cargo build --lib --release --target x86_64-unknown-linux-gnu
	mv librconsole/target/x86_64-unknown-linux-gnu/release/liblibrconsole.so librconsole/target/x86_64-unknown-linux-gnu/release/librconsole.so

librconsole/target/x86_64-pc-windows-gnu/release/librconsole.dll: ${RUST_SOURCE_FILES}
	cd librconsole; \
		cargo build --lib --release --target x86_64-pc-windows-gnu

testjar: librconsole/target/x86_64-unknown-linux-gnu/release/librconsole.so librconsole/target/x86_64-pc-windows-gnu/release/librconsole.dll
	chmod +x gradlew
	rm -rf ./build/resources
	./gradlew testjar

releasejar: librconsole/target/x86_64-unknown-linux-gnu/release/librconsole.so librconsole/target/x86_64-pc-windows-gnu/release/librconsole.dll
	chmod +x gradlew
	rm -rf ./build/resources
	./gradlew releasejar

ghactions: librconsole/target/x86_64-unknown-linux-gnu/release/librconsole.so librconsole/target/x86_64-pc-windows-gnu/release/librconsole.dll
	chmod +x gradlew
	rm -rf ./build/resources
	./gradlew ghActions

web/node_modules:
	cd web; \
		npm i

web/dist/dist.js: web/node_modules
	cd web; \
		npx webpack

web/dist.zip: web/dist/dist.js
	cd web; \
		zip -r dist.zip static/ dist/

clean:
	cd librconsole; \
		cargo clean
	cd web; \
		npm clean
		rm -f dist.zip
	./gradlew cleanEclipse clean
	