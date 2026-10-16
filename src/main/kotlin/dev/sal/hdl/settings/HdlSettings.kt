package dev.sal.hdl.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * IDE-wide settings for the external lint tools, stored in `hdl-support.xml` in the IDE config directory.
 * Edited on Settings → Tools → HDL Support ([HdlSettingsConfigurable]).
 */
@Service(Service.Level.APP)
@State(name = "HdlSettings", storages = [Storage("hdl-support.xml")])
class HdlSettings : PersistentStateComponent<HdlSettings.State> {

    /** Plain mutable bean: the XML serializer needs public `var`s and a no-arg constructor. */
    class State {
        var verilatorEnabled: Boolean = true
        /** Blank means "find `verilator` on PATH". */
        var verilatorPath: String = ""
        /** Include directories passed as `-I<dir>`, separated by `;` or newlines. */
        var verilatorIncludeDirs: String = ""
        var verilatorExtraArgs: String = ""

        var ghdlEnabled: Boolean = true
        /** Blank means "find `ghdl` on PATH". */
        var ghdlPath: String = ""
        /** `--std=` value: "08" or "93". */
        var ghdlStd: String = "08"
        var ghdlExtraArgs: String = ""

        /** How long one lint run may take before it's killed. */
        var timeoutSeconds: Int = 10
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        val GHDL_STANDARDS = listOf("08", "93")

        fun getInstance(): HdlSettings = ApplicationManager.getApplication().getService(HdlSettings::class.java)
    }
}
