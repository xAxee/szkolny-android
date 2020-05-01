/*
 * Copyright (c) Kuba Szczodrzyński 2020-1-4.
 */

package pl.szczodrzynski.edziennik.ui.modules.login

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pl.szczodrzynski.edziennik.*
import pl.szczodrzynski.edziennik.databinding.FragmentLoginFinishBinding
import kotlin.coroutines.CoroutineContext

class LoginFinishFragment : Fragment(), CoroutineScope {
    companion object {
        private const val TAG = "LoginFinishFragment"
    }

    private lateinit var app: App
    private lateinit var activity: LoginActivity
    private lateinit var b: FragmentLoginFinishBinding
    private val nav by lazy { activity.nav }

    private val job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity = (getActivity() as LoginActivity?) ?: return null
        context ?: return null
        app = activity.application as App
        b = FragmentLoginFinishBinding.inflate(inflater)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val firstRun = !App.config.loginFinished
        App.config.loginFinished = true

        if (!firstRun) {
            b.loginFinishSubtitle.setText(R.string.login_finish_subtitle_not_first_run)
        }

        b.finishButton.onClick {
            val firstProfileId = arguments?.getInt("firstProfileId") ?: 0
            if (firstProfileId == 0) {
                activity.finish()
                return@onClick
            }

            app.profileLoad(firstProfileId) {
                if (firstRun) {
                    activity.startActivity(Intent(
                            activity,
                            MainActivity::class.java,
                            "profileId" to firstProfileId,
                            "fragmentId" to MainActivity.DRAWER_ITEM_HOME
                    ))
                }
                else {
                    activity.setResult(Activity.RESULT_OK, Intent(
                            null,
                            "profileId" to firstProfileId,
                            "fragmentId" to MainActivity.DRAWER_ITEM_HOME
                    ))
                }
                activity.finish()
            }
        }
    }
}