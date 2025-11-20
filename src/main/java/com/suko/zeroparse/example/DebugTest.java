package com.suko.zeroparse.example;

import com.jprofiler.api.controller.Controller;
import com.suko.zeroparse.*;

import io.vertx.core.buffer.Buffer;

public class DebugTest {

    public static void main(String[] args) {
        String json2 = "{\"action\":\"snapshot\",\"arg\":{\"instType\":\"USDT-FUTURES\",\"channel\":\"ticker\",\"instId\":\"BTCUSDT\"},\"data\":[{\"instId\":\"BTCUSDT\",\"lastPr\":\"27000.5\",\"bidPr\":\"27000\",\"askPr\":\"27000.5\",\"bidSz\":\"2.71\",\"askSz\":\"8.76\",\"open24h\":\"27000.5\",\"high24h\":\"30668.5\",\"low24h\":\"26999.0\",\"change24h\":\"-0.00002\",\"fundingRate\":\"0.000010\",\"nextFundingTime\":\"1695722400000\",\"markPrice\":\"27000.0\",\"indexPrice\":\"25702.4\",\"holdingAmount\":\"929.502\",\"baseVolume\":\"368.900\",\"quoteVolume\":\"10152429.961\",\"openUtc\":\"27000.5\",\"symbolType\":1,\"symbol\":\"BTCUSDT\",\"deliveryPrice\":\"0\",\"ts\":\"1695715383021\"}],\"ts\":1695715383039}";
        Buffer priceBuffer = Buffer.buffer(json2);
        JsonParser parser = new JsonParser();
        JsonParseContext pooledContext = new JsonParseContext(parser, new com.suko.zeroparse.ViewPools());
        double totalPrice = 0;
        Controller.startAllocRecording(true);
        Controller.startCPURecording(true);
        while (true) {
            pooledContext.close();
            JsonValue jsonValue = pooledContext.parse(priceBuffer);
            double price = jsonValue.asObject().getStringView("lastPr").parseDouble();
            totalPrice += price;
            if(totalPrice > 1000000) {
                totalPrice = 0;
            }
        }
    }

}
