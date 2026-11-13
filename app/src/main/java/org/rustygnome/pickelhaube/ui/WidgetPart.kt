package org.rustygnome.pickelhaube.ui

import kotlinx.coroutines.flow.update
import org.rustygnome.pickelhaube.data.NumberText
import org.rustygnome.pickelhaube.widget.DialWidget
import org.rustygnome.pickelhaube.widget.WidgetEntry
import org.rustygnome.pickelhaube.widget.WidgetList
import org.rustygnome.pickelhaube.widget.WidgetLists

/** The home screen widget "Numbers to dial": its setup in the app, and a tap on one of its rows. */
internal class WidgetPart(private val host: DialerHost) {

    private val state get() = host.state

    /**
     * Opens the setup of the dial widget [id] (placed just now, or its title tapped). With no box set
     * up yet, the welcome tour – or the page for the first box – stays in front: the setup follows
     * once there is a box (see MainActivity).
     */
    fun open(id: Int) {
        if (host.settings.isConfigured()) host.showDialer()
        state.update { it.copy(widgetSetup = WidgetSetup(id, WidgetLists.get(host.app, id))) }
    }

    private fun edit(change: (WidgetList) -> WidgetList) =
        state.update { state -> state.copy(widgetSetup = state.widgetSetup?.let { it.copy(list = change(it.list)) }) }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setTransparent(transparent: Boolean) = edit { it.copy(transparent = transparent) }

    fun remove(index: Int) = edit { it.without(index) }

    /** Moves entry [index] up ([by] -1) or down (+1). */
    fun move(index: Int, by: Int) = edit { it.moved(index, by) }

    /** The number picker, to add a number to the widget being set up. */
    fun startAdding() {
        host.openPicker()
        state.update { it.copy(addingToWidget = true) }
    }

    /** Back from the picker with [number] (and whose it is) – once only, the first place kept. */
    fun add(name: String, number: String) {
        edit { it.plus(WidgetEntry(name, number)) }
        state.update { it.copy(showPicker = false, addingToWidget = false) }
    }

    fun save() {
        val setup = state.value.widgetSetup ?: return
        WidgetLists.set(host.app, setup.id, setup.list.copy(title = setup.list.title.trim()))
        DialWidget.update(host.app, setup.id)
        state.update { it.copy(widgetSetup = null) }
    }

    fun cancel() = state.update { it.copy(widgetSetup = null) }

    /**
     * A row of the dial widget [id] was tapped: its number into the dialer, with whose it is as the
     * field's label – dialled only with the green button, as a tap on the home screen is easily made.
     */
    fun showEntry(id: Int, index: Int) {
        val entry = WidgetLists.get(host.app, id).entries.getOrNull(index) ?: return
        if (host.settings.isConfigured()) host.showDialer()
        state.update {
            it.copy(
                number = NumberText.clean(entry.number),
                numberLabel = entry.name.takeIf(String::isNotBlank)?.let(UiText::Raw),
                backToPicker = false,
            )
        }
    }
}
