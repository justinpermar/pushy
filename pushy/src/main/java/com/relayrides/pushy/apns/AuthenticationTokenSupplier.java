package com.relayrides.pushy.apns;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.util.AsciiString;

class AuthenticationTokenSupplier {

    private final Signature signature;
    private final String issuer;

    private AsciiString token;

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public AuthenticationTokenSupplier(final PrivateKey privateKey, final String issuer) throws NoSuchAlgorithmException, InvalidKeyException {
        this.signature = Signature.getInstance("SHA256withECDSA");
        this.signature.initSign(privateKey);

        this.issuer = issuer;
    }

    public AsciiString getToken() throws SignatureException {
        if (this.token == null) {
            final String header;
            {
                final Map<String, String> headerMap = new HashMap<String, String>();

                headerMap.put("alg", "ES256");
                headerMap.put("typ", "JWT");
                headerMap.put("kid", "TODO");

                header = gson.toJson(headerMap);
            }

            final String claims;
            {
                final Map<String, String> claimsMap = new HashMap<String, String>();

                claimsMap.put("iss", this.issuer);
                claimsMap.put("iat", String.valueOf(System.currentTimeMillis() / 1000));

                claims = gson.toJson(claimsMap);
            }

            final String payloadWithoutSignature = String.format("%s.%s",
                    base64UrlEncodeWithoutPadding(header.getBytes(StandardCharsets.US_ASCII)),
                    base64UrlEncodeWithoutPadding(claims.getBytes(StandardCharsets.US_ASCII)));

            final byte[] signatureBytes;
            {
                this.signature.update(payloadWithoutSignature.getBytes(StandardCharsets.US_ASCII));
                signatureBytes = this.signature.sign();
            }

            this.token = new AsciiString(String.format("%s.%s", payloadWithoutSignature,
                    base64UrlEncodeWithoutPadding(signatureBytes)));
        }

        return this.token;
    }

    public void invalidateToken() {
        this.token = null;
    }

    private static String base64UrlEncodeWithoutPadding(final byte[] bytes) {
        final ByteBuf wrappedString = Unpooled.wrappedBuffer(bytes);
        final ByteBuf encodedString = Base64.encode(wrappedString, Base64Dialect.URL_SAFE);

        final String encodedUnpaddedString = encodedString.toString(StandardCharsets.US_ASCII).replace("=", "");

        wrappedString.release();
        encodedString.release();

        return encodedUnpaddedString;
    }
}