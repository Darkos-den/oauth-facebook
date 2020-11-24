package com.darkos.oauth.engine

import com.darkos.oauth.common.OAuthResultProcessor
import com.darkos.oauth.common.sendAndClose
import com.darkos.oauth.scope.OAuthScope
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import kotlinx.coroutines.channels.Channel

class FacebookAndroidEngine(
    private val resultProcessor: OAuthResultProcessor
) : BaseEngine() {

    //region support objects

    class Config : BaseEngine.Config() {
        var resultProcessor: OAuthResultProcessor? = null
    }

    companion object Engine : OAuthEngine<FacebookAndroidEngine, Config> {
        override fun prepare(block: Config.() -> Unit): FacebookAndroidEngine {
            val config = Config().apply(block)
            return FacebookAndroidEngine(config.resultProcessor!!)
        }
    }

    private inner class Callback : FacebookCallback<LoginResult> {
        override fun onSuccess(loginResult: LoginResult) {
            Result.Success(loginResult.accessToken.token).let {
                resultChannel.sendAndClose(it)
            }
        }

        override fun onCancel() {
            resultChannel.sendAndClose(Result.Canceled)
        }

        override fun onError(error: FacebookException) {
            resultChannel.sendAndClose(Result.Error(error))
        }
    }

    //endregion

    private val resultChannel = Channel<Result>()

    private val callbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }
    private val loginManager: LoginManager by lazy { LoginManager.getInstance() }

    private var permissions = emptyList<String>()

    init {
        resultProcessor.addListener { requestCode, resultCode, data ->
            callbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun applyScopes(scopes: List<OAuthScope>) {
        permissions = scopes.map {
            when (it) {
                OAuthScope.EMAIL -> "email"
                OAuthScope.PROFILE -> "public_profile"
            }
        }
    }

    override suspend fun getAccessToken(): Result {
        loginManager.registerCallback(callbackManager, Callback())
        loginManager.logInWithReadPermissions(resultProcessor.activity, permissions)

        return resultChannel.receive()
    }
}