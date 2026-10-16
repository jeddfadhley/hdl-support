package dev.sal.hdl

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sal.hdl.verilog.VerilogFile
import dev.sal.hdl.verilog.VerilogFileType
import dev.sal.hdl.vhdl.VhdlFile
import dev.sal.hdl.vhdl.VhdlFileType

/** Checks the plugin.xml registrations: file types, PSI files and commenters. */
class HdlFixtureTest : BasePlatformTestCase() {

    fun testVerilogExtensions() {
        for (ext in listOf("v", "vh", "sv", "svh")) {
            val file = myFixture.configureByText("a.$ext", "module m; endmodule\n")
            assertEquals(ext, VerilogFileType, file.virtualFile.fileType)
            assertInstanceOf(file, VerilogFile::class.java)
        }
    }

    fun testVhdlExtensions() {
        for (ext in listOf("vhd", "vhdl")) {
            val file = myFixture.configureByText("a.$ext", "entity e is end;\n")
            assertEquals(ext, VhdlFileType, file.virtualFile.fileType)
            assertInstanceOf(file, VhdlFile::class.java)
        }
    }

    fun testVerilogLineComment() {
        myFixture.configureByText("a.v", "<caret>wire x;\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("//wire x;\n")
    }

    fun testVerilogBlockComment() {
        myFixture.configureByText("a.sv", "<selection>wire x;</selection>\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        myFixture.checkResult("/*wire x;*/\n")
    }

    fun testVhdlLineComment() {
        myFixture.configureByText("a.vhd", "<caret>signal s : bit;\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("--signal s : bit;\n")
    }
}
