package com.zrh.video.compress

import android.app.Dialog
import android.content.Context
import android.view.Gravity

/**
 *
 * @author zrh
 * @date 2023/7/4
 *
 */
class LoadingDialog(context: Context) : Dialog(context, R.style.AlertDialog) {

    init {
        setContentView(R.layout.dialog_loading)
    }

    override fun show() {
        super.show()

        val window = window
        val p = window!!.attributes
        p.gravity = Gravity.CENTER
        window.attributes = p

    }
}