package com.testbird.util.common;

import org.apache.commons.lang3.ArrayUtils;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class AES {
    /**
     * 注意key和加密用到的字符串是不一样的 加密还要指定填充的加密模式和填充模式 AES密钥可以是128或者256，加密模式包括ECB, CBC等
     * ECB模式是分组的模式，CBC是分块加密后，每块与前一块的加密结果异或后再加密 第一块加密的明文是与IV变量进行异或
     */
    private static final String KEY_ALGORITHM = "AES";
    private static final String ECB_CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String CBC_CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int KEY_BYTE_LENGTH_128 = 16;

    /**
     * IV(Initialization Value)是一个初始值，对于CBC模式来说，它必须是随机选取并且需要保密的
     * 而且它的长度和密码分组相同(比如：对于AES 128为128位，即长度为16的byte类型数组)
     */
    private static final byte[] IVPARAMETERS = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    /**
     * 使用ECB模式进行加密，加密过程三步走
     * <p>
     * 1. 传入算法，实例化一个加解密器
     * 2. 传入加密模式和密钥，初始化一个加密器
     * 3. 调用doFinal方法加密
     */
    public static byte[] ecbEncode(byte[] plainText, String key) {
        try {
            SecretKey secretKey = AES.restoreSecretKey(generateAESSecretKey(key));
            Cipher cipher = Cipher.getInstance(ECB_CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(plainText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

    /**
     * 使用ECB解密，三步走
     */
    public static String ecbDecode(byte[] decodedText, String key) {
        try {
            SecretKey secretKey = AES.restoreSecretKey(generateAESSecretKey(key));
            Cipher cipher = Cipher.getInstance(ECB_CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(decodedText));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            // TODO Auto-generated catch block
        }
        return null;

    }

    /**
     * CBC加密，三步走，只是在初始化时加了一个初始变量
     */
    public static byte[] cbcEncode(byte[] plainText, String key) {
        IvParameterSpec ivParameterSpec = new IvParameterSpec(IVPARAMETERS);
        try {
            SecretKey secretKey = AES.restoreSecretKey(generateAESSecretKey(key));
            Cipher cipher = Cipher.getInstance(CBC_CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
            return cipher.doFinal(plainText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | InvalidAlgorithmParameterException
                | IllegalBlockSizeException | BadPaddingException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

    /**
     * CBC 解密
     */
    public static String cbcDecode(byte[] decodedText, String key) {
        IvParameterSpec ivParameterSpec = new IvParameterSpec(IVPARAMETERS);
        try {
            SecretKey secretKey = AES.restoreSecretKey(generateAESSecretKey(key));
            Cipher cipher = Cipher.getInstance(CBC_CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            return new String(cipher.doFinal(decodedText));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | InvalidAlgorithmParameterException
                | IllegalBlockSizeException | BadPaddingException e) {
            // TODO Auto-generated catch block
        }
        return null;
    }

    static byte[] generateAESSecretKey(String s) {
        if (s == null) {
            s = KEY_ALGORITHM + KEY_BYTE_LENGTH_128;
        }
        byte[] bytes = s.getBytes();
        if (bytes.length < KEY_BYTE_LENGTH_128) {
            return ArrayUtils.addAll(bytes, ArrayUtils.subarray(IVPARAMETERS, 0, KEY_BYTE_LENGTH_128 - bytes.length));
        } else {
            return ArrayUtils.subarray(bytes, 0, KEY_BYTE_LENGTH_128);
        }
    }

    /**
     * 还原密钥
     *
     * @param secretBytes
     * @return
     */
    static SecretKey restoreSecretKey(byte[] secretBytes) {
        SecretKey secretKey = new SecretKeySpec(secretBytes, KEY_ALGORITHM);
        return secretKey;
    }
}