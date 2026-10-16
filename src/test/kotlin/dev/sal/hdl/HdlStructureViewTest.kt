package dev.sal.hdl

import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Checks what the structure view shows for the sample files: names, location strings and nesting. */
class HdlStructureViewTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    fun testVerilogCounter() = assertStructure(
        "verilog/counter.v",
        """
        counter (module)
          WIDTH (= 8)
          clk (input wire)
          rst (input wire)
          en (input wire)
          count (output reg [WIDTH-1:0])
          always @(posedge clk)
        """,
    )

    fun testVerilogFsm() = assertStructure(
        "verilog/fsm.sv",
        """
        lights_pkg (package)
          light_t (typedef enum logic [1:0] {RED = 2'b00, GREEN = 2'b01, AMBER = 2'b1x})
        fsm (module)
          clk_i (input logic)
          rst_ni (input logic)
          light_o (output light_t)
          TIMEOUT (localparam int unsigned)
          timer_q (logic [${'$'}clog2(TIMEOUT)-1:0])
          always_ff @(posedge clk_i or negedge rst_ni) (seq)
        """,
    )

    fun testVerilogGenerate() = assertStructure(
        "verilog/generate.sv",
        """
        adder_tree (module)
          a
          b
          sum
          N (parameter int)
          a (input logic [N-1:0][7:0])
          b (input logic [N-1:0][7:0])
          sum (output logic [N-1:0][8:0])
          add (function)
          i (genvar)
          g_lane (begin)
          g_wide (begin)
            u_count (counter)
          g_narrow (begin)
            unused (wire)
          g_one (begin)
            initial
          report_sum (task)
        """,
    )

    fun testVerilogClasses() = assertStructure(
        "verilog/classes.sv",
        """
        bus_if (interface)
          clk (input logic)
          data (logic [7:0])
          valid (logic)
        base_item (class)
        packet (class)
          W (= 8)
          payload (rand bit [W-1:0])
          count (static int)
          new (function)
          describe (function)
        cov_tb (module)
          clk (logic)
          bus (bus_if)
          pkt (packet #(8))
          cg (covergroup)
          initial
        """,
    )

    fun testVhdlCounter() = assertStructure(
        "vhdl/counter.vhd",
        """
        Counter (entity)
          WIDTH (Positive)
          clk (in std_logic)
          rst (in std_logic)
          count (out std_logic_vector(WIDTH - 1 downto 0))
        rtl (of Counter)
          value (unsigned(count'range))
          tick (process (clk))
        """,
    )

    fun testVhdlTop() = assertStructure(
        "vhdl/top.vhd",
        """
        top (entity)
          clk (in std_logic)
          rst (in std_logic)
          leds (out std_logic_vector(7 downto 0))
        structural (of top)
          bus_t (record)
          Counter (component)
            WIDTH (positive)
            clk (in std_logic)
            rst (in std_logic)
            count (out std_logic_vector(WIDTH - 1 downto 0))
          state (state_t)
          counts (std_logic_vector(15 downto 0))
          u_direct (counter)
          u_component (Counter)
          g_leds (for i in 0 to 7 generate)
          g_debug (if DELAY > 0 ns generate)
            seen (boolean)
          fsm (process (clk))
        top_cfg (of top)
        """,
    )

    fun testVhdlPackage() = assertStructure(
        "vhdl/package.vhd",
        """
        util_pkg (package)
          MASK (std_logic_vector(7 downto 0))
          BITS (std_logic_vector)
          LIMIT (integer)
          DELAY (time)
          state_t ((IDLE, BUSY, DONE))
          char_set (('a', 'b', '''))
          parity (function)
        util_pkg (package body)
          parity (function)
        """,
    )

    private fun assertStructure(path: String, expected: String) {
        myFixture.configureByFile(path)
        myFixture.testStructureView { component ->
            assertEquals(expected.trimIndent(), render(component))
        }
    }

    /** One line per node: `text (location)`, indented two spaces per level, in source order. */
    private fun render(component: StructureViewComponent): String = buildString {
        fun print(element: TreeElement, depth: Int) {
            val presentation = element.presentation
            if (isNotEmpty()) append('\n')
            append("  ".repeat(depth)).append(presentation.presentableText)
            presentation.locationString?.let { append(" (").append(it).append(")") }
            for (child in element.children) print(child, depth + 1)
        }
        for (child in component.treeModel.root.children) print(child, 0)
    }
}
