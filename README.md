# BaseBukkitPlugin
For Spigot 1.16

I use this as a base template for my plugins.

### Comes with: 
- Main class
- Configuration Handler
- GitHub Actions workflow to create a Release with an attached JAR when you push a tag

### Easy testing:
``gradle testJar`` will put a jar in server/plugins

### Releasing: 
``gradle releaseJar`` will put a jar in releases/

### Configuration for Gradle
You can configure everything in gradle.properties. Values set here will also be set in plugin.yml
