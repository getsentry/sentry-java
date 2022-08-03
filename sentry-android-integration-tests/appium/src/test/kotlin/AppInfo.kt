import java.nio.file.Path

class AppInfo(val name: String, val activity: String, path: Path) {
    val path: Path = path.toRealPath()
}
