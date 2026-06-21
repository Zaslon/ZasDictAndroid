package com.zaslon.zasdict.data

object DropboxConfig {
    const val REDIRECT_URI = "zasdict://dropbox-auth"
    const val AUTH_URL = "https://www.dropbox.com/oauth2/authorize"
    const val TOKEN_URL = "https://api.dropbox.com/oauth2/token"
    const val DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download"
    const val UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload"
    const val LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder"
    const val LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue"
    const val CURRENT_ACCOUNT_URL = "https://api.dropboxapi.com/2/users/get_current_account"
}
