package com.turtleshelldevelopment.utils.db;

import com.turtleshelldevelopment.BackendServer;
import com.turtleshelldevelopment.utils.Issuers;
import com.turtleshelldevelopment.utils.mfa.MultiFactorResponse;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

public class Account {
    protected byte[] salt;
    protected byte[] passwordHash;
    protected SecretKeyFactory factory;
    private int userId, siteId, userType;
    private final String username;
    private String firstName;
    private String lastName;
    private String email;

    public static Account getAccountInfo(String username) {
        try(PreparedStatement accountCall = BackendServer.database.getConnection().prepareStatement("SELECT user_id, username, first_name, last_name, site_id, user_type, email FROM User WHERE username = ?;")) {
            accountCall.setString(1, username);
            ResultSet set = accountCall.executeQuery();
            if(set.next()) {
                return new Account(set.getInt("user_id"), set.getString("username"),
                        set.getString("first_name"), set.getString("last_name"),
                        set.getInt("site_id"), set.getInt("user_type"),
                        set.getString("email"));
            } else {
                return null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private Account(int userID, String username, String firstName, String lastName, int siteId, int userType, String email) {
        this.userId = userID;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.siteId = siteId;
        this.userType = userType;
        this.email = email;
    }

    public Account(String username, String password) {
        this.username = username;
        try {
            salt = getSalt();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 64 * 8);
        try {
            factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            passwordHash = factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Updates this Account instance with user information from the database
     * @return This Account Instance with Account Information
     */
    public Account getAccountInfo() {
        try(CallableStatement accountCall = BackendServer.database.getConnection().prepareCall("CALL GET_USER(?)")) {
            accountCall.setString(1, username);
            ResultSet set = accountCall.executeQuery();
            if(set.next()) {
                setUserId(set.getInt("user_id"));
                setFirstName(set.getString("first_name"));
                setLastName(set.getString("last_name"));
                setSiteId(set.getInt("site_id"));
                setUserType(set.getInt("user_type"));
                setEmail(set.getString("email"));
            }
        } catch (SQLException e) {
            return this;
        }
        return this;
    }

    public byte[] getPasswordSalt() {
        return this.salt;
    }

    public byte[] getPasswordHash() {
        return this.passwordHash;
    }

    private static byte[] getSalt() throws NoSuchAlgorithmException
    {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return salt;
    }

    public MultiFactorResponse generateTOTPMultiFactor() throws QrGenerationException {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        QrData qr = new QrData.Builder()
                .label("Covid-19 Dashboard: " + this.username)
                .secret(secret)
                .issuer(Issuers.AUTHENTICATION.getIssuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        QrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData = generator.generate(qr);
        String mimeType = generator.getImageMimeType();
        String dataUri = getDataUriForImage(imageData, mimeType);
        return new MultiFactorResponse(secret, dataUri, qr.getUri());
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public int getUserType() {
        return userType;
    }

    public void setUserType(int userType) {
        this.userType = userType;
    }

    public int getSiteId() {
        return siteId;
    }

    public void setSiteId(int siteId) {
        this.siteId = siteId;
    }

    public int getUserId() {
        return userId;
    }

    private void setUserId(int userId) {
        this.userId = userId;
    }

}