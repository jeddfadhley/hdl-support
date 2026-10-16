/*
 * Traffic light FSM (SystemVerilog).
 */
package lights_pkg;
    typedef enum logic [1:0] {RED = 2'b00, GREEN = 2'b01, AMBER = 2'b1x} light_t;
endpackage

module fsm
    import lights_pkg::*;
(
    input  logic   clk_i,
    input  logic   rst_ni,
    output light_t light_o
);
    localparam int unsigned TIMEOUT = 32'd1_000;
    logic [$clog2(TIMEOUT)-1:0] timer_q;

    always_ff @(posedge clk_i or negedge rst_ni) begin : seq
        if (!rst_ni) begin
            light_o <= RED;
            timer_q <= '0;
        end else begin
            timer_q <= timer_q + 1;
            unique case (light_o)
                RED:     if (timer_q == TIMEOUT - 1) light_o <= GREEN;
                GREEN:   light_o <= AMBER;
                default: light_o <= RED;
            endcase
        end
    end

    assert property (@(posedge clk_i) disable iff (!rst_ni) light_o != AMBER |=> light_o == RED);
endmodule : fsm
