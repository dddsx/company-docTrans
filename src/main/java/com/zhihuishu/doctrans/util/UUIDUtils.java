package com.zhihuishu.doctrans.util;

import java.util.UUID;

public class UUIDUtils {

    public static String createUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }
}
