// SystemVerilog classes, an interface and a covergroup.
interface bus_if (input logic clk);
    logic [7:0] data;
    logic       valid;
    modport master (output data, valid);
endinterface : bus_if

virtual class base_item;
    pure virtual function string describe();
endclass

class packet #(int W = 8) extends base_item;
    rand bit [W-1:0] payload;
    static int count = 0;
    constraint small_payload { payload < 100; }

    function new();
        count++;
    endfunction

    virtual function string describe();
        return $sformatf("packet %0d", payload);
    endfunction

    extern task send(virtual bus_if vif);
endclass : packet

module cov_tb;
    logic clk;
    bus_if bus (.clk(clk));
    packet #(8) pkt = new();

    covergroup cg @(posedge clk);
        coverpoint bus.data { bins low = {[0:127]}; bins high = {[128:255]}; }
    endgroup

    initial fork
        #5 clk = 1;
        pkt.randomize();
    join_none
endmodule
