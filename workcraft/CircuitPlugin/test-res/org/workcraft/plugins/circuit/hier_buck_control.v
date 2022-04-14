// Verilog netlist generated by Workcraft 3
module CTRL (uv, oc, zc, gp_ack, gp, gn_ack, gn);
    input uv, oc, zc, gp_ack, gn_ack;
    output gp, gn;
    wire charge_ctrl_chrg_ack, cycle_ctrl_chrg_req;

    CHARGE_CTRL charge_ctrl (.gp(gp), .gn(gn), .gp_ack(gp_ack), .gn_ack(gn_ack), .chrg_req(cycle_ctrl_chrg_req), .oc(oc), .zc(zc), .chrg_ack(charge_ctrl_chrg_ack));
    CYCLE_CTRL cycle_ctrl (.chrg_req(cycle_ctrl_chrg_req), .chrg_ack(charge_ctrl_chrg_ack), .uv(uv));

    // signal values at the initial state:
    // !gp !gn !charge_ctrl_chrg_ack !cycle_ctrl_chrg_req !uv !oc !zc !gp_ack !gn_ack
endmodule

module CHARGE_CTRL (oc, zc, gp_ack, gp, gn_ack, gn, chrg_ack, chrg_req);
    input oc, zc, gp_ack, gn_ack, chrg_req;
    output gp, gn, chrg_ack;
    wire wait_zc_san, charge_oc_ctrl, charge_zc_ctrl, wait_oc_san;

    WAIT wait_zc (.san(wait_zc_san), .ctrl(charge_zc_ctrl), .sig(zc));
    CHARGE charge (.gp(gp), .gn(gn), .gp_ack(gp_ack), .gn_ack(gn_ack), .chrg_req(chrg_req), .oc_san(wait_oc_san), .zc_san(wait_zc_san), .chrg_ack(chrg_ack), .oc_ctrl(charge_oc_ctrl), .zc_ctrl(charge_zc_ctrl));
    WAIT wait_oc (.san(wait_oc_san), .ctrl(charge_oc_ctrl), .sig(oc));

    // signal values at the initial state:
    // !wait_zc_san !gp !gn !chrg_ack !charge_oc_ctrl !charge_zc_ctrl !wait_oc_san !oc !zc !gp_ack !gn_ack !chrg_req
endmodule

module CYCLE_CTRL (uv, chrg_ack, chrg_req);
    input uv, chrg_ack;
    output chrg_req;
    wire cycle_uv_ctrl, wait2_uv_san;

    CYCLE cycle (.chrg_req(chrg_req), .chrg_ack(chrg_ack), .uv_san(wait2_uv_san), .uv_ctrl(cycle_uv_ctrl));
    WAIT2 wait2_uv (.san(wait2_uv_san), .ctrl(cycle_uv_ctrl), .sig(uv));

    // signal values at the initial state:
    // !chrg_req !cycle_uv_ctrl !wait2_uv_san !uv !chrg_ack
endmodule

module CHARGE (chrg_req, gn_ack, gp_ack, oc_san, zc_san, chrg_ack, gn, gp, oc_ctrl, zc_ctrl);
    input chrg_req, gn_ack, gp_ack, oc_san, zc_san;
    output chrg_ack, gn, gp, oc_ctrl, zc_ctrl;
    wire _U1_ON, _U3_ON, _U4_ON, _U6_O, _U7_ON, _U8_ON, _U9_ON, _U10_ON, _U11_ON, _U12_ON;

    BUF oc_ctrla (.O(oc_ctrl), .I(gp));
    BUF zc_ctrla (.O(zc_ctrl), .I(gn));
    NOR2 _U0 (.ON(chrg_ack), .A(gn_ack), .B(_U9_ON));
    // This inverter should have a short delay
    INV _U1 (.ON(_U1_ON), .I(gn));
    AOI211 _U2 (.ON(gn), .A1(_U1_ON), .A2(zc_san), .B(gp_ack), .C(_U12_ON));
    NAND4B _U3 (.ON(_U3_ON), .AN(oc_san), .B(chrg_req), .C(_U9_ON), .D(_U12_ON));
    INV _U4 (.ON(_U4_ON), .I(_U3_ON));
    C2 _U5 (.Q(gp), .A(_U4_ON), .B(_U12_ON));
    AND2 _U6 (.O(_U6_O), .A(oc_san), .B(gp_ack));
    // This inverter should have a short delay
    INV _U7 (.ON(_U7_ON), .I(_U6_O));
    // This inverter should have a short delay
    INV _U8 (.ON(_U8_ON), .I(_U9_ON));
    OAI211 _U9 (.ON(_U9_ON), .A1(_U8_ON), .A2(gn_ack), .B(chrg_req), .C(_U7_ON));
    // This inverter should have a short delay
    INV _U10 (.ON(_U10_ON), .I(zc_san));
    // This inverter should have a short delay
    INV _U11 (.ON(_U11_ON), .I(_U12_ON));
    OAI22 _U12 (.ON(_U12_ON), .A1(_U11_ON), .A2(_U6_O), .B1(_U9_ON), .B2(_U10_ON));

    // signal values at the initial state:
    // !oc_ctrl !zc_ctrl !chrg_ack _U1_ON !gn _U3_ON !_U4_ON !gp !_U6_O _U7_ON !_U8_ON _U9_ON _U10_ON !_U11_ON _U12_ON !chrg_req !gn_ack !gp_ack !oc_san !zc_san
endmodule

module CYCLE (chrg_ack, chrg_req, uv_ctrl, uv_san);
    input chrg_ack, uv_san;
    output chrg_req, uv_ctrl;
    wire me_r1_ON, me_r2_ON;

    INV me_r1 (.ON(me_r1_ON), .I(uv_san));
    INV me_r2 (.ON(me_r2_ON), .I(chrg_ack));
    MUTEX me (.r1(me_r1_ON), .g1(uv_ctrl), .r2(me_r2_ON), .g2(chrg_req));

    // signal values at the initial state:
    // me_r1_ON !me_r2_ON uv_ctrl !chrg_req !chrg_ack !uv_san
endmodule

module WAIT2 (sig, ctrl, san);
    input sig, ctrl;
    output san;

    // signal values at the initial state:
    // !ctrl !san !sig
endmodule
