import * as fs from 'fs'
import * as crypto from 'crypto'
import axios from 'axios'
import * as path from 'path'
import * as FormData from 'form-data'
import { AppInfo } from './appinfo'

const serverUri = 'https://api.us-west-1.saucelabs.com'

// Note: node-saucelab uploadApp API is broken as of 7.2.0
//   const sauceLabs = new SauceLabs(sauceOptions)
//   const body = fs.createReadStream(app.path)
//   const response = await sauceLabs.uploadApp(appType, appIdentifier, appDisplayName, appActive, body)
// Instead, we use manual POST request:
// https://docs.saucelabs.com/dev/api/storage/#upload-file-to-app-storage
const uploadApp = async (sauceOptions: SauceLabsOptions, app: AppInfo): Promise<string> => {
    console.log(`Uploading app ${app.name} to SauceLabs from ${app.path}`)

    const fileId = await findAppOnServer(sauceOptions, app)
    if (fileId != undefined) {
        console.log(`Skipping app ${app.name} upload - the same file already exists on the server as ${fileId}`)
        return fileId
    }

    const form = new FormData()
    form.append('payload', fs.createReadStream(app.path))
    form.append('name', path.basename(app.path))

    const response = await axios({
        auth: {
            username: sauceOptions.user,
            password: sauceOptions.key
        },
        method: 'post',
        url: serverUri + '/v1/storage/upload',
        data: form,
        headers: form.getHeaders(),
    })

    if (response.status != 201) {
        throw `Invalid status code when uploading app: ${response.status} ${response.statusText}\n${response.data}`
    }

    console.log(`App ${app.name} uploaded successfully`)
    return response.data.item.id
}

// https://docs.saucelabs.com/dev/api/storage/#get-app-storage-files
const findAppOnServer = async (sauceOptions: SauceLabsOptions, app: AppInfo): Promise<string | undefined> => {
    const hash = crypto.createHash('sha256')
    hash.update(fs.readFileSync(app.path))

    const response = await axios({
        auth: {
            username: sauceOptions.user,
            password: sauceOptions.key
        },
        method: 'get',
        url: serverUri + '/v1/storage/files?sha256=' + hash.digest('hex'),
    })

    if (response.status != 200) {
        throw `Invalid status code when checking if an app already exists: ${response.status} ${response.statusText}\n${response.data}`
    }

    if (response.data.items.length > 0) {
        return response.data.items[0].id
    }

    return undefined
}

export { uploadApp, findAppOnServer }