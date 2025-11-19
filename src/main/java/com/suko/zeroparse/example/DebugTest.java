package com.suko.zeroparse.example;

import com.suko.zeroparse.*;

public class DebugTest {

    public static void main(String[] args) {
        try (JsonParseContext ctx = new JsonParseContext()) {
            JsonObject value = ctx.parse("{\"name\":\"John\",\"age\":30}").asObject();
            String name = value.get("name").toString();
            int age = value.get("age").asNumber().asInt();

            System.out.println(name);
            System.out.println(age);
        }
    }

}
