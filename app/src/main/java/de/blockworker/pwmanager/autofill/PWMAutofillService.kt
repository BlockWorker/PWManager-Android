package de.blockworker.pwmanager.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.autofill.inline.v1.InlineSuggestionUi
import de.blockworker.pwmanager.R
import de.blockworker.pwmanager.settings.SettingsActivity


class PWMAutofillService : AutofillService() {

    private lateinit var icon: Icon

    override fun onCreate() {
        super.onCreate()

        icon = Icon.createWithResource(applicationContext, R.mipmap.ic_launcher)
            .setTintBlendMode(BlendMode.DST)
    }

    @SuppressLint("RestrictedApi")
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        Log.d("Autofill", "onFillRequest: called")

        val attribIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val context: List<FillContext> = request.fillContexts
        val structure: AssistStructure = context[context.size - 1].structure

        val pkg = structure.activityComponent.packageName

        val passFields = findPasswordFields(structure)
        Log.d("Autofill", passFields.size.toString())

        if (passFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val passwordPresentation = RemoteViews(packageName, android.R.layout.activity_list_item)
        passwordPresentation.setTextViewText(android.R.id.text1, "PWManager")
        passwordPresentation.setImageViewResource(android.R.id.icon, R.mipmap.ic_launcher)
        var inlinePresentation: InlinePresentation? = null

        val inlineReq = request.inlineSuggestionsRequest
        if (inlineReq != null) {
            val content = InlineSuggestionUi.newContentBuilder(attribIntent)
                .setTitle("PWManager")
                .setContentDescription("Generate password with PWManager")
                .setStartIcon(icon)
                .build()

            inlinePresentation = InlinePresentation(content.slice,
                inlineReq.inlinePresentationSpecs.first(), false)
        }

        val responseBuilder = FillResponse.Builder()

        for ((id, domain) in passFields) {
            val datasetBuilder = Dataset.Builder()

            if (inlinePresentation != null) {
                datasetBuilder.setValue(id, null, passwordPresentation, inlinePresentation)
            } else {
                datasetBuilder.setValue(id, null, passwordPresentation)
            }

            val intent = Intent(this, AutofillActivity::class.java)
                .putExtra(EXTRA_AUTOFILL_ID, id)
                .putExtra(EXTRA_WEBSITE, domain)
                .putExtra(EXTRA_PACKAGE, pkg)

            datasetBuilder.setAuthentication(PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).intentSender)

            responseBuilder.addDataset(datasetBuilder.build())
        }

        callback.onSuccess(responseBuilder.build())
    }

    private fun findPasswordFields(structure: AssistStructure): List<Pair<AutofillId, String?>> {
        val list = ArrayList<Pair<AutofillId, String?>>()
        for (i in 0 until structure.windowNodeCount) {
            addPasswordFields(list, structure.getWindowNodeAt(i).rootViewNode)
        }
        return list
    }

    private fun addPasswordFields(list: ArrayList<Pair<AutofillId, String?>>, node: ViewNode) {
        if (node.autofillId != null &&
            node.autofillHints?.any { it.contains("password", ignoreCase = true) } == true) {
            list.add(Pair(node.autofillId!!, node.webDomain))
        }
        for (i in 0 until node.childCount) {
            addPasswordFields(list, node.getChildAt(i))
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

}