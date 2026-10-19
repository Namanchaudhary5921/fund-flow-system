package com.fundflow.util;

import org.mindrot.jbcrypt.BCrypt;

/** Password hashing with BCrypt (salted, slow on purpose, so brute-force is hard). */
public final class PasswordUtil {
    private PasswordUtil() {}
    public static String hash(String plain) { return BCrypt.hashpw(plain, BCrypt.gensalt(10)); }
    public static boolean verify(String plain, String hash) { return BCrypt.checkpw(plain, hash); }
}
