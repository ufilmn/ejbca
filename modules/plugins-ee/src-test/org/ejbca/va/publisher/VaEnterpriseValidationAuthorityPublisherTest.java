/*************************************************************************
 *                                                                       *
 *  EJBCA - Proprietary Modules: Enterprise Certificate Authority        *
 *                                                                       *
 *  Copyright (c), PrimeKey Solutions AB. All rights reserved.           *
 *  The use of the Proprietary Modules are subject to specific           * 
 *  commercial license terms.                                            *
 *                                                                       *
 *************************************************************************/
package org.ejbca.va.publisher;

import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.cesecore.CaTestUtils;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificate.CertificateData;
import org.cesecore.certificates.certificate.CertificateDataWrapper;
import org.cesecore.certificates.certificate.CertificateInfo;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.TraceLogMethodsRule;
import org.ejbca.core.model.ca.publisher.PublisherConnectionException;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A collection of system tests for the VA Publisher using EnterpriseValidationAuthorityPublisher, extracted from the org.ejbca.core.model.ca.publisher.PublisherTest and VaPublisherTest.
 * Cribbed from org.ejbca.core.model.ca.publisher.PublisherTest
 *
 * @version $Id: VaEnterpriseValidationAuthorityPublisherTest.java 27422 2018-04-30 14:05:42Z andrey_s_helmes $
 */
public class VaEnterpriseValidationAuthorityPublisherTest extends VaPublisherTestBase {

    private String publisherName = "TEST_EVA_PUBLISHER";
    private EnterpriseValidationAuthorityPublisher enterpriseValidationAuthorityPublisher;
    private int publisherId = 0;
    // Set of variables to track for objects to be removed as test results
    private Certificate testCertificate;
    private Certificate testOcspCertificate;
    private Certificate testSubCaCertificate;
    private List<Integer> publishers;
    private String caName = null;
    private String endEntityManagementUsername = null;
    private List<String> certificateProfileUsernames = new ArrayList<>();
    private X509Certificate x509Certificate = null;
    private String endEntityProfileUsername = null;

    @Rule
    public TestRule traceLogMethodsRule = new TraceLogMethodsRule();

    @BeforeClass
    public static void beforeClass() {
        CryptoProviderTools.installBCProvider();
    }

    @Before
    public void setUp() throws CertificateParsingException, PublisherExistsException, AuthorizationDeniedException {
        testCertificate = CertTools.getCertfromByteArray(testCertificateBytes, Certificate.class);
        testOcspCertificate = CertTools.getCertfromByteArray(testOcpsSignerCertificateBytes, Certificate.class);
        testSubCaCertificate = CertTools.getCertfromByteArray(testSubCaCertificateBytes, Certificate.class);
        publishers = new ArrayList<>();
        enterpriseValidationAuthorityPublisher = createEnterpriseValidationAuthorityPublisher();
        publisherId = publisherProxySession.addPublisher(
                internalAdminToken,
                publisherName,
                enterpriseValidationAuthorityPublisher);
        publishers.add(publisherId);
    }

    @After
    public void tearDown() throws Exception {
        // Remove certificate
        internalCertStoreSession.removeCertificate(testCertificate);
        internalCertStoreSession.removeCertificate(testOcspCertificate);
        // Flush publishers
        for (int publisherEntry : publishers) {
            publisherProxySession.removePublisherInternal(internalAdminToken, publisherProxySession.getPublisherName(publisherEntry));
        }
        // Remove CA if exists
        if(caName != null) {
            final CAInfo caInfo = caSession.getCAInfo(internalAdminToken, caName);
            cryptoTokenManagementSession.deleteCryptoToken(internalAdminToken, caInfo.getCAToken().getCryptoTokenId());
            caSession.removeCA(internalAdminToken, caInfo.getCAId());
        }
        // Remove end entity if exists
        if(endEntityManagementUsername != null) {
            if (endEntityManagementSession.existsUser(endEntityManagementUsername)) {
                endEntityManagementSession.deleteUser(internalAdminToken, endEntityManagementUsername);
            }
        }
        // Remove certificate profiles if exists
        if(!certificateProfileUsernames.isEmpty()) {
            for(String certificateProfileUsername : certificateProfileUsernames) {
                certificateProfileSession.removeCertificateProfile(internalAdminToken, certificateProfileUsername);
            }
        }
        // Remove certificate if exists
        if(x509Certificate != null) {
            internalCertStoreSession.removeCertificate(x509Certificate);
        }
        // Remove end entity profile if exists
        if(endEntityProfileUsername != null) {
            endEntityProfileSession.removeEndEntityProfile(internalAdminToken, endEntityProfileUsername);
        }
    }

    @Test
    public void shouldPublishCertificateWithStatusRemoveFromCrlInRevokedOnlyMode() throws AuthorizationDeniedException, PublisherConnectionException, CertificateParsingException {
        // given
        publisherProxySession.testConnection(publisherId);
        // activate only publish when revoked
        switchEnterpriseValidationAuthorityPublisherInRevokedOnlyMode();
        final CertificateData revokedCertificateData = createCertificateDataUsingTestCertificateAndCustomData(
                "test",
                null,
                CertificateConstants.CERT_ACTIVE,
                CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER,
                null,
                System.currentTimeMillis() + 12345,
                RevokedCertInfo.REVOCATION_REASON_REMOVEFROMCRL,
                -1L);
        // when
        final boolean revocationResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                createCertificateDataWrapperUsingCertificateData(revokedCertificateData),
                "foo123",
                CertTools.getSubjectDN(testCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(testCertificate));
        // then
        assertTrue("Error storing certificate to external ocsp publisher", revocationResult);
        assertNull("The certificate should not exist in the DB.", actualCertificateInfo);
    }

    @Test
    public void shouldNotPublishCertificateWithStatusNotRevokedInRevokedOnlyMode() throws AuthorizationDeniedException, PublisherConnectionException, CertificateParsingException {
        // given
        publisherProxySession.testConnection(publisherId);
        // activate only publish when revoked
        switchEnterpriseValidationAuthorityPublisherInRevokedOnlyMode();
        // when
        final boolean additionResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                createCertificateDataWrapperUsingTestCertificateData(),
                "foo123",
                CertTools.getSubjectDN(testCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(testCertificate));
        // then
        assertTrue("Error storing certificate to external ocsp publisher", additionResult);
        assertNull("The certificate should not exist in the DB.", actualCertificateInfo);
    }

    @Test
    public void shouldPublishProperCertificateWithStatusRevokedInRevokedOnlyMode() throws AuthorizationDeniedException, PublisherConnectionException, CertificateParsingException {
        // given
        publisherProxySession.testConnection(publisherId);
        final String expectedUsername = "someUser";
        final String expectedCaFingerprint = "Some CA fingerprint";
        final int expectedStatus = CertificateConstants.CERT_REVOKED;
        final int expectedProfileId = CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER;
        final String expectedTag = "someTag";
        long expectedTime = System.currentTimeMillis();
        final int expectedRevocationReason = RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED;
        // activate only publish when revoked
        switchEnterpriseValidationAuthorityPublisherInRevokedOnlyMode();
        final CertificateData revokedCertificateData = createCertificateDataUsingTestCertificateAndCustomData(
                expectedUsername,
                expectedCaFingerprint,
                expectedStatus,
                expectedProfileId,
                expectedTag,
                expectedTime,
                expectedRevocationReason,
                System.currentTimeMillis() - 1);
        // when
        final boolean revocationResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                createCertificateDataWrapperUsingCertificateData(revokedCertificateData),
                "foo123",
                CertTools.getSubjectDN(testCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(testCertificate));
        // then
        assertTrue("Error storing certificate to external ocsp publisher", revocationResult);
        assertEquals(expectedStatus, actualCertificateInfo.getStatus());
        assertEquals(expectedProfileId, actualCertificateInfo.getCertificateProfileId());
        assertEquals(expectedTag, actualCertificateInfo.getTag());
        assertEquals(expectedTime, actualCertificateInfo.getUpdateTime().getTime());
        assertEquals(expectedUsername, actualCertificateInfo.getUsername());
        assertEquals(expectedCaFingerprint, actualCertificateInfo.getCAFingerprint());
    }

    @Test
    public void shouldSupportIssuingThrowAwayCertificateByThrowAwayCaWithoutCertificateDataPersistence() throws Exception {
        // given
        caName = "testCaTa";
        final String caSubjectDn = "CN=" + caName;
        final String certificateUsername = "testCaTaUser";
        final String certificateDn = "CN=" + certificateUsername;
        final String certificatePassword = "foo123";
        certificateProfileUsernames.add(caName);
        final int testCaCertificateProfileId = certificateProfileSession.addCertificateProfile(
                internalAdminToken,
                caName,
                createCertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA, publisherId, 0,true));
        CaTestUtils.createX509ThrowAwayCa(internalAdminToken, caName, caName, caSubjectDn, testCaCertificateProfileId);
        final CAInfo testCaInfo = caSession.getCAInfo(internalAdminToken, caName);
        final int testCaId =  testCaInfo.getCAId();
        certificateProfileUsernames.add(certificateUsername);
        endEntityProfileUsername = certificateUsername;
        endEntityManagementUsername = certificateUsername;
        final int certificateProfileId = certificateProfileSession.addCertificateProfile(
                internalAdminToken,
                certificateUsername,
                createCertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, publisherId, testCaId,true));
        final int endEntityProfileId = endEntityProfileSession.addEndEntityProfile(internalAdminToken, certificateUsername, createEndEntityProfile(certificateProfileId, testCaId));
        x509Certificate = createThrowAwayX509Certificate(certificateUsername, certificatePassword, certificateDn, endEntityProfileId, certificateProfileId, testCaId);
        final String x509CertificateFingerprint = CertTools.getFingerprintAsString(x509Certificate);
        // when
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(x509CertificateFingerprint);
        final Certificate actualCertificate = certificateStoreSession.findCertificateByFingerprint(x509CertificateFingerprint);
        // then
        assertTrue("Creating External OCSP Publisher failed", 0 != publisherId);
        assertNotNull("Cannot create throw-away certificate.", x509Certificate);
        assertNull("The certificate info should not exist in the DB.", actualCertificateInfo);
        assertNull("The certificate should not exist in the DB.", actualCertificate);
    }

    @Test
    public void shouldPublishOcspCertificateWithFullDataWhenNoMetaDataMode() throws PublisherConnectionException, AuthorizationDeniedException, CertificateParsingException {
        // given
        publisherProxySession.testConnection(publisherId);
        // Don't store sensitive meta data
        switchEnterpriseValidationAuthorityPublisherInNoMetaDataMode();
        // When
        final Certificate certificate = CertTools.getCertfromByteArray(testOcpsSignerCertificateBytes, Certificate.class);
        final CertificateData ocspSignerCert = new CertificateData(
                certificate,
                certificate.getPublicKey(),
                "OcspSigner",
                "abcde1234",
                "csr1234",
                RevokedCertInfo.NOT_REVOKED,
                CertificateConstants.CERTTYPE_ENDENTITY,
                CertificateProfileConstants.CERTPROFILE_FIXED_OCSPSIGNER,
                EndEntityConstants.NO_END_ENTITY_PROFILE,
                "tag",
                System.currentTimeMillis() + 12345,
                true,
                true);
        CertificateDataWrapper certWrapper = createCertificateDataWrapperUsingCertificateData(ocspSignerCert);
        final boolean additionResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                certWrapper,
                "foo123",
                CertTools.getSubjectDN(testOcspCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(ocspSignerCert.getFingerprint());
        // then
        assertTrue("Error storing certificate to external ocsp publisher", additionResult);
        assertEquals("OcspSigner", actualCertificateInfo.getUsername());
        assertEquals(CertTools.getSubjectDN(testOcspCertificate), actualCertificateInfo.getSubjectDN());
    }
    
    @Test
    public void shouldPublishCertificateWithLimitedDataWhenNoMetaDataMode() throws CertificateParsingException, AuthorizationDeniedException, PublisherConnectionException {
        // given
        publisherProxySession.testConnection(publisherId);
        // Don't store sensitive meta data
        switchEnterpriseValidationAuthorityPublisherInNoMetaDataMode();
        // when
        final boolean additionResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                createCertificateDataWrapperUsingTestCertificateData(),
                "foo123",
                CertTools.getSubjectDN(testCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(CertTools.getFingerprintAsString(testCertificate));
        // then
        assertTrue("Error storing certificate to external ocsp publisher", additionResult);
        assertNotNull("Expected issuer DN to be stored in publisher target database", actualCertificateInfo.getIssuerDN());
        assertEquals(null, actualCertificateInfo.getUsername());
        assertEquals(0, actualCertificateInfo.getEndEntityProfileIdOrZero());
        assertEquals(EnterpriseValidationAuthorityPublisher.HIDDEN_VALUE, actualCertificateInfo.getSubjectDN());
        assertEquals(null, actualCertificateInfo.getTag());
    }
    
    @Test
    public void shouldPublishCertificateOfTypeSubCa() throws PublisherConnectionException, CertificateParsingException, AuthorizationDeniedException {
        // given
        publisherProxySession.testConnection(publisherId);
        final Certificate certificate = CertTools.getCertfromByteArray(testSubCaCertificateBytes, Certificate.class);
        final CertificateData subCaCert = new CertificateData(
                certificate,
                certificate.getPublicKey(),
                "SubCaEe",
                "abcde1234",
                "csr1234",
                RevokedCertInfo.NOT_REVOKED,
                CertificateConstants.CERTTYPE_SUBCA,
                CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA,
                EndEntityConstants.NO_END_ENTITY_PROFILE,
                "tag",
                System.currentTimeMillis() + 12345,
                true,
                true);
        final CertificateDataWrapper certWrapper = createCertificateDataWrapperUsingCertificateData(subCaCert);
        // when
        final boolean additionResult = publisherSession.storeCertificate(
                internalAdminToken,
                publishers,
                certWrapper,
                "foo123",
                CertTools.getSubjectDN(testSubCaCertificate),
                null);
        final CertificateInfo actualCertificateInfo = certificateStoreSession.getCertificateInfo(subCaCert.getFingerprint());
        // then
        assertTrue("Error storing Sub CA certificate Publisher", additionResult);
        assertEquals("Unexpected certificate type of published entry", CertificateConstants.CERTTYPE_SUBCA, actualCertificateInfo.getType());
        assertEquals("Unexpected Subject DN of published entry", CertTools.getSubjectDN(testSubCaCertificate), actualCertificateInfo.getSubjectDN());
    }
    
    private void switchEnterpriseValidationAuthorityPublisherInNoMetaDataMode() throws AuthorizationDeniedException {
        enterpriseValidationAuthorityPublisher.setPropertyData(enterpriseValidationAuthorityPublisher.getPropertyData() + 
                EnterpriseValidationAuthorityPublisher.PROPERTYKEY_DONTSTORECERTIFICATEMETADATA + "=" + true + "\n");
        publisherSession.changePublisher(internalAdminToken, publisherName, enterpriseValidationAuthorityPublisher);
    }
    
    private void switchEnterpriseValidationAuthorityPublisherInRevokedOnlyMode() throws AuthorizationDeniedException {
        enterpriseValidationAuthorityPublisher.setPropertyData(enterpriseValidationAuthorityPublisher.getPropertyData() + 
                EnterpriseValidationAuthorityPublisher.PROPERTYKEY_ONLYREVOKED + "=" + true + "\n");
        publisherSession.changePublisher(internalAdminToken, publisherName, enterpriseValidationAuthorityPublisher);
    }
}
