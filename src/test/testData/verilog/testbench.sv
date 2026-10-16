`include "defs.svh"
`define HALF_PERIOD 5

module tb;
    logic clk = 1'b0;
    logic [7:0] data;
    wire \bus.out[0] ;
    real ratio = 0.5e-3;

    counter #(.WIDTH(8)) dut (.clk(clk), .rst(1'b0), .en(1'b1), .count(data));

    always #`HALF_PERIOD clk = ~clk;

    initial begin
        $dumpfile("tb.vcd");
        $dumpvars(0, tb);
        repeat (10) @(posedge clk);
        #10ns;
        if (data !== 8'hxx) $display("data = %h (\"ok\")", data);
        else $error("unexpected: %b", 8'bzzzz_0101);
        $finish;
    end
endmodule
