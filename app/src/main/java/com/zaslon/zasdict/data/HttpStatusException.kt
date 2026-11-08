package com.zaslon.zasdict.data

import java.io.IOException

/**
 * 通信先が失敗の HTTP ステータスを返したことを表す。
 * 画面側はステータスから原因（認証切れ・競合など）を利用者向けの文に言い換えるため、
 * 例外メッセージの文字列ではなく [code] で判定する。
 */
class HttpStatusException(val code: Int, message: String) : IOException(message)
