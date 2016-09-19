package com.xored.javafx.packeteditor.scapy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZMQ;

import java.util.Base64;

public class ScapyServerClient {
    static Logger log = LoggerFactory.getLogger(ScapyServerClient.class);

    Gson gson;
    ZMQ.Context context;
    ZMQ.Socket session;
    String version_handler;
    int last_id = 0;
    Base64.Encoder base64Encoder = Base64.getEncoder();

    static class Request {
        final String jsonrpc = "2.0";
        String id;
        String method;
        JsonElement params;
    }

    static class Response {
        String jsonrpc;
        String id;
        JsonElement result;
        JsonObject error;
    }

    public boolean open(String url) {
        close();
        context = ZMQ.context(1);
        session = context.socket(ZMQ.REQ);
        try {
            session.connect(url);
        } catch (Exception e) {
            log.error("Failed connect to {} - {}", url, e);
            close();
            return false;
        }

        gson = new Gson();

        JsonElement r = request("get_version", null);
        if (r == null || !r.isJsonObject()) {
            log.error("Failed to get Scapy version");
            return false;
        }
        log.debug("Scapy version is {}", r.getAsJsonObject().getAsJsonPrimitive("version").getAsString());

        JsonArray vers = new JsonArray();
        vers.add("1");
        vers.add("01");
        r = request("get_version_handler", vers);
        if (r == null || !r.isJsonPrimitive()) {
            log.error("Failed to get Scapy version handler");
            return false;
        }
        version_handler = r.getAsString();
        return true;
    }

    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }

        if (context != null) {
            context.term();
            context = null;
        }
    }

    public JsonElement request(String method, JsonElement params) {
        Request reqs = new Request();
        reqs.id = Integer.toString(++last_id);
        reqs.method = method;
        reqs.params = params;

        String s = gson.toJson(reqs);
        log.debug(" sending: {}", s);

        session.send(s.getBytes(), 0);

        byte[] b = session.recv(0);
        if (b == null) {
            log.debug("? recv null");
            return null;
        }

        String r = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        log.debug("received: {}", r);

        Response resp;
        try {
            resp = gson.fromJson(r, Response.class);
        } catch (Exception e) {
            log.error("JSON parse failed", e);
            return null;
        }

        if (resp == null) {
            log.error("fromJson returned null");
            return null;
        }

        if (!resp.id.equals(reqs.id)) {
            log.error("received id:{}, expected:{}", resp.id, reqs.id);
            return null;
        }

        if (resp.error != null) {
            log.error("received error: {}", resp.error.get("message"));
            return null;
        }

        return resp.result;
    }

    public ScapyPkt build_pkt(JsonElement params) {
        JsonArray payload = new JsonArray();
        payload.add(version_handler);
        payload.add(params);
        ScapyPkt pkt = new ScapyPkt(request("build_pkt", payload));
        return pkt;
    }

    /* reads first packet from binary pcap file */
    public ScapyPkt read_pcap_packet(byte[] binary) {
        JsonArray payload = new JsonArray();
        String payload_b64 = base64Encoder.encodeToString(binary);
        payload.add(version_handler);
        payload.add(payload_b64);
        JsonArray pcap_packets = (JsonArray)request("read_pcap", payload);
        ScapyPkt pkt = new ScapyPkt(pcap_packets.get(0));
        return pkt;
    }

    public JsonElement get_tree() {
        JsonArray payload = new JsonArray();
        payload.add(version_handler);
        return request("get_tree", payload);
    }

}
