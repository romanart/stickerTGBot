package database

import DatabaseConfig
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.rds.auth.GetIamAuthTokenRequest
import com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.*


class AWSDatabaseConnection(databaseConfig: DatabaseConfig) : DatabaseConnection {
    private val creds = DefaultAWSCredentialsProviderChain()
    private val AWS_ACCESS_KEY = creds.credentials.awsAccessKeyId
    private val AWS_SECRET_KEY = creds.credentials.awsSecretKey

    //Configuration parameters for the generation of the IAM Database Authentication token
    private val RDS_INSTANCE_HOSTNAME = databaseConfig.host
    private val RDS_INSTANCE_PORT = databaseConfig.port
    private val REGION_NAME = databaseConfig.region
    private val DB_NAME = databaseConfig.databaseName
    private val DB_USER = databaseConfig.user
    private val DB_PASSWORD = databaseConfig.password
    private val JDBC_URL = "jdbc:mysql://$RDS_INSTANCE_HOSTNAME:$RDS_INSTANCE_PORT/$DB_NAME"

    private val SSL_CERTIFICATE = databaseConfig.certificate

    private val KEY_STORE_TYPE = "JKS"
    private val KEY_STORE_PROVIDER = "SUN"
    private val KEY_STORE_FILE_PREFIX = "sys-connect-via-ssl-test-cacerts"
    private val KEY_STORE_FILE_SUFFIX = ".jks"
    private val DEFAULT_KEY_STORE_PASSWORD = "changeit"

    private val connection = getDBConnectionUsingIam()

    private fun createCertificate(): X509Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        val url = File(SSL_CERTIFICATE).toURI().toURL() ?: throw Exception()
        url.openStream().use { certInputStream ->
            return certFactory.generateCertificate(certInputStream) as X509Certificate
        }
    }

    private fun createKeyStoreFile(rootX509Certificate: X509Certificate): File {
        val keyStoreFile: File = File.createTempFile(KEY_STORE_FILE_PREFIX, KEY_STORE_FILE_SUFFIX)
        FileOutputStream(keyStoreFile.path).use { fos ->
            val ks: KeyStore = KeyStore.getInstance(KEY_STORE_TYPE, KEY_STORE_PROVIDER)
            ks.load(null)
            ks.setCertificateEntry("rootCaCertificate", rootX509Certificate)
            ks.store(fos, DEFAULT_KEY_STORE_PASSWORD.toCharArray())
        }
        return keyStoreFile
    }

    private fun createKeyStoreFile(): String {
        return createKeyStoreFile(createCertificate()).path
    }

    private fun clearSslProperties() {
        System.clearProperty("javax.net.ssl.trustStore")
        System.clearProperty("javax.net.ssl.trustStoreType")
        System.clearProperty("javax.net.ssl.trustStorePassword")
    }

    private fun setSslProperties() {
        System.setProperty("javax.net.ssl.trustStore", createKeyStoreFile())
        System.setProperty("javax.net.ssl.trustStoreType", KEY_STORE_TYPE)
        System.setProperty("javax.net.ssl.trustStorePassword", DEFAULT_KEY_STORE_PASSWORD)
    }

    private fun getDBConnectionUsingIam(): Connection {
        try {
            setSslProperties()
            return DriverManager.getConnection(JDBC_URL, setMySqlConnectionProperties())
        } finally {
            clearSslProperties()
        }
    }

    private fun setMySqlConnectionProperties(): Properties {
        val mysqlConnectionProperties = Properties()
        mysqlConnectionProperties.setProperty("verifyServerCertificate", "true")
        mysqlConnectionProperties.setProperty("useSSL", "true")
        mysqlConnectionProperties.setProperty("user", DB_USER)
        mysqlConnectionProperties.setProperty("password", DB_PASSWORD ?: generateAuthToken())
        return mysqlConnectionProperties
    }

    private fun generateAuthToken(): String {
        val awsCredentials = BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
        val generator = RdsIamAuthTokenGenerator.builder()
            .credentials(AWSStaticCredentialsProvider(awsCredentials))
            .region(REGION_NAME)
            .build()
        return generator.getAuthToken(GetIamAuthTokenRequest.builder()
            .hostname(RDS_INSTANCE_HOSTNAME).port(RDS_INSTANCE_PORT).userName(DB_USER).build())
    }


    override fun <R> executeQuery(sql: String, processor: (ResultSet) -> R): R {
        return connection.createStatement().use {
            processor(it.executeQuery(sql))
        }
    }

    override fun executeUpdate(sql: String) {
        connection.createStatement().use {
            it.executeUpdate(sql)
        }
    }

    override fun releaseConnection() {
        connection.close()
        clearSslProperties()
    }


}