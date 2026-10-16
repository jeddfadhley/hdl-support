package dev.sal.hdl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sal.hdl.navigation.HdlUnitIndex
import dev.sal.hdl.verilog.psi.VerilogDesignUnit
import dev.sal.hdl.vhdl.psi.VhdlComponent
import dev.sal.hdl.vhdl.psi.VhdlDesignUnit

/** Go-to-definition from module, entity and package names to their declarations. */
class HdlNavigationTest : BasePlatformTestCase() {

    // ---- Verilog ----

    fun testVerilogInstanceGoesToModuleInAnotherFile() {
        myFixture.addFileToProject("counter.v", "module counter(input clk); endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; coun<caret>ter u_count (.clk(1'b0)); endmodule\n")
        assertTarget<VerilogDesignUnit>("counter", "counter.v")
    }

    fun testVerilogImportGoesToPackage() {
        myFixture.addFileToProject("pkg.sv", "package lights_pkg; endpackage\n")
        myFixture.configureByText("fsm.sv", "module fsm import lights<caret>_pkg::*; (); endmodule\n")
        assertTarget<VerilogDesignUnit>("lights_pkg", "pkg.sv")
    }

    fun testVerilogExtendsGoesToBaseClass() {
        myFixture.configureByText("c.sv", "class base; endclass\nclass derived extends ba<caret>se; endclass\n")
        assertTarget<VerilogDesignUnit>("base", "c.sv")
    }

    fun testVerilogNamesAreCaseSensitive() {
        myFixture.addFileToProject("counter.v", "module Counter; endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; coun<caret>ter u (); endmodule\n")
        assertNull(myFixture.getReferenceAtCaretPositionWithAssertion().resolve())
    }

    fun testVerilogInstancePrefersModuleOverPackageOfTheSameName() {
        myFixture.addFileToProject("a.sv", "package util; endpackage\nmodule util; endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; ut<caret>il u (); endmodule\n")
        val target = assertTarget<VerilogDesignUnit>("util", "a.sv")
        assertEquals(VerilogDesignUnit.Kind.MODULE, target.kind)
    }

    fun testDuplicateModulesOfferAChoice() {
        myFixture.addFileToProject("a/counter.v", "module counter; endmodule\n")
        myFixture.addFileToProject("b/counter.v", "module counter; endmodule\n")
        myFixture.configureByText("tb.sv", "module tb; coun<caret>ter u (); endmodule\n")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion() as PsiPolyVariantReference
        assertEquals(2, reference.multiResolve(false).size)
    }

    fun testUnknownModuleDoesNotResolve() {
        myFixture.configureByText("tb.sv", "module tb; miss<caret>ing u (); endmodule\n")
        assertNull(myFixture.getReferenceAtCaretPositionWithAssertion().resolve())
    }

    // ---- VHDL ----

    fun testVhdlArchitectureGoesToEntityIgnoringCase() {
        myFixture.addFileToProject("counter_e.vhd", "entity Counter is end entity;\n")
        myFixture.configureByText("counter_a.vhd", "architecture rtl of COUN<caret>TER is begin end;\n")
        assertTarget<VhdlDesignUnit>("Counter", "counter_e.vhd")
    }

    fun testVhdlEntityInstanceGoesToEntity() {
        myFixture.addFileToProject("counter.vhd", "entity counter is end;\narchitecture rtl of counter is begin end;\n")
        myFixture.configureByText(
            "top.vhd",
            "architecture s of top is begin u : entity work.coun<caret>ter(rtl) port map (clk => clk); end;\n",
        )
        val target = assertTarget<VhdlDesignUnit>("counter", "counter.vhd")
        assertEquals(VhdlDesignUnit.Kind.ENTITY, target.kind)
    }

    fun testVhdlComponentInstanceFallsBackToComponentDeclaration() {
        myFixture.configureByText(
            "top.vhd",
            """
            architecture s of top is
                component blackbox port (a : in bit); end component;
            begin
                u : black<caret>box port map (a => '0');
            end;
            """.trimIndent(),
        )
        assertTarget<VhdlComponent>("blackbox", "top.vhd")
    }

    fun testVhdlInstanceCanNameAVerilogModule() {
        myFixture.addFileToProject("counter.v", "module counter(input clk); endmodule\n")
        myFixture.configureByText("top.vhd", "architecture s of top is begin u : Coun<caret>ter port map (clk); end;\n")
        assertTarget<VerilogDesignUnit>("counter", "counter.v")
    }

    fun testVhdlUseClauseGoesToPackageNotBody() {
        myFixture.addFileToProject(
            "util.vhd",
            "package util_pkg is end package;\npackage body util_pkg is end package body;\n",
        )
        myFixture.configureByText("top.vhd", "use work.util<caret>_pkg.all;\nentity top is end;\n")
        val target = assertTarget<VhdlDesignUnit>("util_pkg", "util.vhd")
        assertEquals(VhdlDesignUnit.Kind.PACKAGE, target.kind)
    }

    // ---- Index ----

    fun testVerilogIndexFindsUnitDeclarationsOnly() {
        val names = HdlUnitIndex.verilogUnitNames(
            """
            module automatic m1; endmodule
            interface i1; endinterface
            virtual class c1; endclass
            interface class c2; endclass
            typedef class c3;
            package p1; endpackage
            module m2; virtual interface i1 vif; endmodule
            // module in_comment;
            """.trimIndent(),
        )
        assertEquals(listOf("m1", "i1", "c1", "c2", "c2", "p1", "m2"), names)
    }

    fun testVhdlIndexFindsEntitiesAndPackagesOnly() {
        val names = HdlUnitIndex.vhdlUnitNames(
            """
            ENTITY E1 IS END;
            package p1 is end;
            package body p1 is end;
            architecture a of e1 is begin u : entity work.e2 port map (x); end;
            -- entity in_comment is
            """.trimIndent(),
        )
        assertEquals(listOf("E1", "p1"), names)
    }

    /** Resolves the reference at the caret and checks the target's type, name and file. */
    private inline fun <reified T : PsiElement> assertTarget(name: String, fileName: String): T {
        val target = myFixture.getReferenceAtCaretPositionWithAssertion().resolve()
        assertInstanceOf(target, T::class.java)
        assertEquals(name, (target as PsiNamedElement).name)
        assertEquals(fileName, target.containingFile.name)
        return target as T
    }
}
