export class AppInfo {
    startupTimes!: number[]

    constructor(
        public name: string,
        public activity: string,
        public path: string,
    ) {
        this.path = this.path.replace(/\\/g, '/')
    }
}