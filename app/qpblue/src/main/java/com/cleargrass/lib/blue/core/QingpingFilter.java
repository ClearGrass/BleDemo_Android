package com.cleargrass.lib.blue.core;

import java.util.ArrayList;
import java.util.List;

public final class QingpingFilter {
    private ArrayList<Byte> productIds;
    private ArrayList<String> macList;

    private boolean isRequestBinding;
    private boolean isRequestBooting;

    private static String[] hexList = new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

    public static QingpingFilter build(boolean isRequestBinding, boolean isRequestBooting, byte[] productIds, List<String> macList) {
        QingpingFilter filter = new QingpingFilter();
        filter.isRequestBinding = isRequestBinding;
        filter.isRequestBooting = isRequestBooting;
        filter.productIds = new ArrayList<>();

        if (productIds != null) {
            for (int i = 0; i < productIds.length; i++) {
                filter.productIds.add((byte) (productIds[i] & 0xFF));
            }
        }

        filter.macList = new ArrayList<>();
        if (macList != null) {
            for (int i = 0; i < macList.size(); i++) {
                String m = macList.get(i);
                filter.macList.add(m.toUpperCase());
            }
        }


        return filter;
    }

    private QingpingFilter() {

    }


    boolean bytesMatched(final byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return false;
        }

        int valueCursor = 0;
        if ((bytes.length > 14)) {
            if ((bytes[6] == (byte) 0xFD && bytes[5] == (byte) 0xCD)
                    || (bytes[6] == (byte) 0xFF && bytes[5] == (byte) 0xF9)) {
                valueCursor = 7;
            } else {
                return false;
            }
        } else {
            return false;
        }

        byte frameControl = bytes[valueCursor];

        boolean binding = (frameControl & 0x02) > 0;
        boolean booting = (frameControl & 0x04) > 0;

        if (binding && booting) {
            return false;
        }

        if (isRequestBinding != binding) {
            return false;
        }

        if (isRequestBooting != booting) {
            return false;
        }

        byte productId = bytes[valueCursor + 1];
        if (productIds.size() > 0 && productIds.indexOf(productId) < 0) {
            return false;
        }

        if (macList.size() > 0) {
            // mac list
            int cursor = valueCursor + 7;
            StringBuilder sb = new StringBuilder();
            while (cursor >= valueCursor + 2) {

                byte b = bytes[cursor];
                sb.append(hexList[((b >> 4) & 0x0F)]);
                sb.append(hexList[b & 0x0F]);

                cursor -= 1;
            }

            String mac = sb.toString();
            return macList.indexOf(mac) >= 0;
        }

        return true;
    }

}
