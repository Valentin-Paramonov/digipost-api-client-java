/**
 * Copyright (C) Posten Norge AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.digipost.api.client.internal;

import no.digipost.api.client.ApiService;
import no.digipost.api.client.delivery.DocumentContent;
import no.digipost.api.client.delivery.MessageDeliverer;
import no.digipost.api.client.delivery.OngoingDelivery.SendableForPrintOnly;
import no.digipost.api.client.errorhandling.DigipostClientException;
import no.digipost.api.client.errorhandling.ErrorCode;
import no.digipost.api.client.representations.Channel;
import no.digipost.api.client.representations.DigipostAddress;
import no.digipost.api.client.representations.DigipostUri;
import no.digipost.api.client.representations.Document;
import no.digipost.api.client.representations.EncryptionKey;
import no.digipost.api.client.representations.FileType;
import no.digipost.api.client.representations.Identification;
import no.digipost.api.client.representations.IdentificationResult;
import no.digipost.api.client.representations.IdentificationResultWithEncryptionKey;
import no.digipost.api.client.representations.Link;
import no.digipost.api.client.representations.MayHaveSender;
import no.digipost.api.client.representations.Message;
import no.digipost.api.client.representations.MessageDelivery;
import no.digipost.api.client.representations.MessageRecipient;
import no.digipost.api.client.representations.MessageStatus;
import no.digipost.api.client.representations.NorwegianAddress;
import no.digipost.api.client.representations.PersonalIdentificationNumber;
import no.digipost.api.client.representations.PrintDetails;
import no.digipost.api.client.representations.PrintRecipient;
import no.digipost.api.client.representations.SmsNotification;
import no.digipost.api.client.representations.sender.SenderInformation;
import no.digipost.api.client.security.CryptoUtil;
import no.digipost.api.client.security.FakeEncryptionKey;
import no.digipost.api.client.testing.MockfriendlyResponse;
import no.digipost.print.validate.PdfValidationSettings;
import no.digipost.print.validate.PdfValidator;
import no.digipost.time.ControllableClock;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static java.util.stream.Stream.concat;
import static no.digipost.api.client.DigipostClientConfig.newConfiguration;
import static no.digipost.api.client.pdf.EksempelPdf.pdf20Pages;
import static no.digipost.api.client.pdf.EksempelPdf.printablePdf1Page;
import static no.digipost.api.client.pdf.EksempelPdf.printablePdf2Pages;
import static no.digipost.api.client.representations.AuthenticationLevel.PASSWORD;
import static no.digipost.api.client.representations.Channel.PRINT;
import static no.digipost.api.client.representations.Message.MessageBuilder.newMessage;
import static no.digipost.api.client.representations.MessageStatus.DELIVERED;
import static no.digipost.api.client.representations.MessageStatus.DELIVERED_TO_PRINT;
import static no.digipost.api.client.representations.Relation.GET_ENCRYPTION_KEY;
import static no.digipost.api.client.representations.SensitivityLevel.NORMAL;
import static no.digipost.api.client.representations.sender.SenderFeatureName.DELIVERY_DIRECT_TO_PRINT;
import static no.digipost.api.client.representations.sender.SenderFeatureName.DIGIPOST_DELIVERY;
import static no.digipost.api.client.representations.sender.SenderFeatureName.PRINTVALIDATION_FONTS;
import static no.digipost.api.client.representations.sender.SenderFeatureName.PRINTVALIDATION_MARGINS_LEFT;
import static no.digipost.api.client.representations.sender.SenderFeatureName.PRINTVALIDATION_PDFVERSION;
import static no.digipost.api.client.representations.sender.SenderStatus.VALID_SENDER;
import static no.digipost.api.client.util.JAXBContextUtils.jaxbContext;
import static no.digipost.api.client.util.JAXBContextUtils.marshal;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class MessageSenderTest {

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private static final Logger LOG = LoggerFactory.getLogger(MessageSenderTest.class);

    static {
        CryptoUtil.addBouncyCastleProviderAndVerify_AES256_CBC_Support();
    }

    @Mock
    private CloseableHttpResponse mockClientResponse;

    @Mock
    private CloseableHttpResponse mockClientResponse2;

    @Mock
    private ApiService api;

    @Mock
    private IdentificationResultWithEncryptionKey identificationResultWithEncryptionKey;

    private MockfriendlyResponse encryptionKeyResponse;

    private final ControllableClock clock = ControllableClock.freezedAt(Instant.now());

    @Spy
    private PdfValidator pdfValidator;

    private MessageSender sender;
    private MessageSender cachelessSender;
    private EncryptionKey fakeEncryptionKey;

    @Before
    public void setup() {
        this.fakeEncryptionKey = FakeEncryptionKey.createFakeEncryptionKey();
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext, fakeEncryptionKey, bao);

        encryptionKeyResponse = MockfriendlyResponse.MockedResponseBuilder.create()
                .status(SC_OK)
                .entity(new ByteArrayEntity(bao.toByteArray()))
                .build();

        sender = new MessageSender(newConfiguration().build(), api, new DocumentsPreparer(pdfValidator), clock);

        cachelessSender = new MessageSender(newConfiguration().disablePrintKeyCache().build(), api, new DocumentsPreparer(pdfValidator), clock);
    }


    @Test
    public void skalHenteEksisterendeForsendelseHvisDenFinnesFraForr() {
        Message forsendelseIn = lagDefaultForsendelse();

        when(mockClientResponse.getStatusLine()).thenReturn(new StatusLineMock(SC_CONFLICT));
        when(mockClientResponse.getFirstHeader(anyString())).thenReturn(new BasicHeader("head", "er"));

        when(api.createMessage(forsendelseIn)).thenReturn(mockClientResponse);

        MessageDelivery eksisterendeForsendelse = new MessageDelivery(forsendelseIn.messageId, Channel.DIGIPOST, MessageStatus.NOT_COMPLETE, null);

        when(mockClientResponse2.getStatusLine()).thenReturn(new StatusLineMock(SC_OK));

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext, eksisterendeForsendelse, bao);
        HttpEntity forsendelse = new ByteArrayEntity(bao.toByteArray());

        when(mockClientResponse2.getEntity()).thenReturn(forsendelse);
        when(api.fetchExistingMessage((URI) any())).thenReturn(mockClientResponse2);

        MessageDelivery delivery = sender.createOrFetchMessage(forsendelseIn);
        then(api).should().fetchExistingMessage(any(URI.class));

        assertTrue(delivery.isSameMessageAs(forsendelseIn));
    }

    @Test
    public void skalKasteFeilHvisForsendelseAlleredeLevert() {
        Message forsendelseIn = lagDefaultForsendelse();

        when(mockClientResponse.getStatusLine()).thenReturn(new StatusLineMock(SC_CONFLICT));
        when(mockClientResponse.getFirstHeader(anyString())).thenReturn(new BasicHeader("head", "er"));
        when(api.createMessage(forsendelseIn)).thenReturn(mockClientResponse);

        MessageDelivery eksisterendeForsendelse = new MessageDelivery(forsendelseIn.messageId, Channel.DIGIPOST, DELIVERED, now());
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext, eksisterendeForsendelse, bao);

        when(mockClientResponse2.getStatusLine()).thenReturn(new StatusLineMock(SC_OK));
        when(mockClientResponse2.getEntity()).thenReturn(new ByteArrayEntity(bao.toByteArray()));
        when(api.fetchExistingMessage((URI) any())).thenReturn(mockClientResponse2);

        try {
            sender.createOrFetchMessage(forsendelseIn);
            fail();
        } catch (DigipostClientException e) {
            assertEquals(ErrorCode.DIGIPOST_MESSAGE_ALREADY_DELIVERED, e.getErrorCode());
        }

    }

    @Test
    public void skalKasteFeilHvisForsendelseAlleredeLevertTilPrint() {
        Message forsendelseIn = lagDefaultForsendelse();

        when(mockClientResponse.getStatusLine()).thenReturn(new StatusLineMock(SC_CONFLICT));
        when(mockClientResponse.getFirstHeader(anyString())).thenReturn(new BasicHeader("head", "er"));
        when(api.createMessage(forsendelseIn)).thenReturn(mockClientResponse);

        MessageDelivery eksisterendeForsendelse = new MessageDelivery(forsendelseIn.messageId, PRINT, DELIVERED_TO_PRINT, now());
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext, eksisterendeForsendelse, bao);

        when(mockClientResponse2.getStatusLine()).thenReturn(new StatusLineMock(SC_OK));
        when(mockClientResponse2.getEntity()).thenReturn(new ByteArrayEntity(bao.toByteArray()));
        when(api.fetchExistingMessage((URI) any())).thenReturn(mockClientResponse2);

        try {
            sender.createOrFetchMessage(forsendelseIn);
            fail();
        } catch (DigipostClientException e) {
            assertEquals(ErrorCode.PRINT_MESSAGE_ALREADY_DELIVERED, e.getErrorCode());
        }

    }

    @Test
    public void skal_bruke_cached_print_encryption_key() {
        when(api.getEncryptionKeyForPrint()).thenReturn(encryptionKeyResponse);

        sender.getEncryptionKeyForPrint();
        then(api).should(times(1)).getEncryptionKeyForPrint();

        clock.timePasses(ofMinutes(5));
        sender.getEncryptionKeyForPrint();
        then(api).should(times(1)).getEncryptionKeyForPrint();

        clock.timePasses(ofMillis(1));
        sender.getEncryptionKeyForPrint();
        then(api).should(times(2)).getEncryptionKeyForPrint();
    }

    @Test
    public void skal_ikke_bruke_cached_print_encryption_key_da_encryption_er_avskrudd() {
        when(api.getEncryptionKeyForPrint()).thenReturn(encryptionKeyResponse);

        cachelessSender.getEncryptionKeyForPrint();
        then(api).should(times(1)).getEncryptionKeyForPrint();

        cachelessSender.getEncryptionKeyForPrint();
        then(api).should(times(2)).getEncryptionKeyForPrint();

        clock.timePasses(ofMinutes(10));
        cachelessSender.getEncryptionKeyForPrint();
        then(api).should(times(3)).getEncryptionKeyForPrint();
    }


    @Test
    public void fallback_to_print_changes_filetype_html_to_pdf() {
        IdentificationResultWithEncryptionKey identificationResultWithEncryptionKey =
                new IdentificationResultWithEncryptionKey(IdentificationResult.digipost("123"), fakeEncryptionKey);

        when(mockClientResponse.getStatusLine()).thenReturn(new StatusLineMock(200));

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext, identificationResultWithEncryptionKey, bao);

        when(mockClientResponse.getEntity()).thenReturn(new ByteArrayEntity(bao.toByteArray()));

        when(api.identifyAndGetEncryptionKey(any(Identification.class))).thenReturn(mockClientResponse);

        CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        ByteArrayOutputStream bao2 = new ByteArrayOutputStream();
        marshal(jaxbContext,
                new MessageDelivery(UUID.randomUUID().toString(), Channel.PRINT, MessageStatus.COMPLETE, now()), bao2);

        when(response.getEntity()).thenReturn(new ByteArrayEntity(bao2.toByteArray()));
        when(response.getStatusLine()).thenReturn(new StatusLineMock(200));

        when(api.multipartMessage(any(HttpEntity.class))).thenReturn(response);

        String messageId = UUID.randomUUID().toString();
        final Document printDocument = new Document(UUID.randomUUID().toString(), "subject", FileType.HTML).encrypt();
        final List<Document> printAttachments = asList(new Document(UUID.randomUUID().toString(), "attachment", FileType.HTML).encrypt());
        PrintRecipient recipient = new PrintRecipient("Rallhild Ralleberg", new NorwegianAddress("0560", "Oslo"));
        PrintRecipient returnAddress = new PrintRecipient("Megacorp", new NorwegianAddress("0105", "Oslo"));

        Map<String, DocumentContent> documentAndContent = new LinkedHashMap<>();

        MessageSender messageSender = new MessageSender(newConfiguration().build(), api, new DocumentsPreparer(pdfValidator), clock);
        Message message = newMessage(messageId, printDocument).attachments(printAttachments)
                .recipient(new MessageRecipient(new DigipostAddress("asdfasd"), new PrintDetails(recipient, returnAddress))).build();

        documentAndContent.put(message.primaryDocument.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf1Page()));
        for (Document attachment : printAttachments) {
            documentAndContent.put(attachment.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf1Page()));
        }

        messageSender.sendMultipartMessage(message, documentAndContent);
    }

    @Test
    public void setDigipostContentToUUIDTest(){
        Document printDocument = new Document(UUID.randomUUID().toString(), "subject", FileType.HTML).encrypt();
        Map<String, DocumentContent> documentAndContent = new LinkedHashMap<>();
        PrintRecipient recipient = new PrintRecipient("Rallhild Ralleberg", new NorwegianAddress("0560", "Oslo"));
        PrintRecipient returnAddress = new PrintRecipient("Megacorp", new NorwegianAddress("0105", "Oslo"));

        List<Document> printAttachments = asList(new Document(UUID.randomUUID().toString(), "attachment", FileType.HTML).encrypt());
        Message message = newMessage(UUID.randomUUID().toString(), printDocument).attachments(printAttachments)
                .recipient(new MessageRecipient(new DigipostAddress("asdfasd"), new PrintDetails(recipient, returnAddress))).build();

        documentAndContent.put(message.primaryDocument.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf2Pages()));
        for (Document attachment : printAttachments) {
            documentAndContent.put(attachment.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf2Pages()));
        }

        Map<Document, InputStream> documentAndInputStreams = new HashMap<>();
        Message digipostCopyMessage = Message.copyMessageWithOnlyDigipostDetails(message);
        MessageSender.setDigipostContentToUUID(documentAndContent, documentAndInputStreams, digipostCopyMessage.getAllDocuments());

        digipostCopyMessage.getAllDocuments().forEach(doc -> {
            InputStream inputStream = documentAndInputStreams.get(doc);
            assertThat(inputStream, is(documentAndContent.get(doc.uuid).getDigipostContent()));
        });

        assertThat(digipostCopyMessage.recipient.hasPrintDetails(), is(false));
        assertThat(digipostCopyMessage.recipient.hasDigipostIdentification(),is(true));
    }

    @Test
    public void setPrintContentToUUIDTest(){
        Document printDocument = new Document(UUID.randomUUID().toString(), "subject", FileType.HTML).encrypt();
        Map<String, DocumentContent> documentAndContent = new LinkedHashMap<>();
        PrintRecipient recipient = new PrintRecipient("Rallhild Ralleberg", new NorwegianAddress("0560", "Oslo"));
        PrintRecipient returnAddress = new PrintRecipient("Megacorp", new NorwegianAddress("0105", "Oslo"));

        List<Document> printAttachments = asList(new Document(UUID.randomUUID().toString(), "attachment", FileType.HTML).encrypt());
        Message message = newMessage(UUID.randomUUID().toString(), printDocument).attachments(printAttachments)
                .recipient(new MessageRecipient(new DigipostAddress("asdfasd"), new PrintDetails(recipient, returnAddress))).build();

        documentAndContent.put(message.primaryDocument.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf2Pages()));
        for (Document attachment : printAttachments) {
            documentAndContent.put(attachment.uuid, DocumentContent.CreateMultiStreamContent(printablePdf1Page(), printablePdf2Pages()));
        }

        Map<Document, InputStream> documentAndInputStreams = new HashMap<>();
        Message printCopyMessage = Message.copyMessageWithOnlyPrintDetails(message);
        MessageSender.setPrintContentToUUID(documentAndContent, documentAndInputStreams, printCopyMessage.getAllDocuments());

        printCopyMessage.getAllDocuments().forEach(doc -> {
            InputStream inputStream = documentAndInputStreams.get(doc);
            assertThat(inputStream, is(documentAndContent.get(doc.uuid).getPrintContent()));
        });

        assertThat(printCopyMessage.recipient.hasPrintDetails(), is(true));
        assertThat(printCopyMessage.recipient.hasDigipostIdentification(),is(false));
    }

    @Test
    public void passes_pdf_validation_for_printonly_message() throws IOException {
        String messageId = UUID.randomUUID().toString();
        when(api.getEncryptionKeyForPrint()).thenReturn(encryptionKeyResponse);
        when(api.multipartMessage(any(HttpEntity.class))).thenReturn(mockClientResponse);
        when(mockClientResponse.getStatusLine()).thenReturn(new StatusLineMock(SC_OK));

        final Document printDocument = new Document(UUID.randomUUID().toString(), "subject", FileType.PDF).encrypt();
        final List<Document> printAttachments = asList(new Document(UUID.randomUUID().toString(), "attachment", FileType.PDF).encrypt());

        concat(Stream.of(printDocument), printAttachments.stream()).forEach(document -> document.addLink(new Link(GET_ENCRYPTION_KEY, new DigipostUri("/encrypt"))));

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        marshal(jaxbContext,
                new MessageDelivery(messageId, PRINT, DELIVERED_TO_PRINT, now()), bao);

        when(mockClientResponse.getEntity())
                .thenReturn(new ByteArrayEntity(bao.toByteArray()));


        PrintRecipient recipient = new PrintRecipient("Rallhild Ralleberg", new NorwegianAddress("0560", "Oslo"));
        PrintRecipient returnAddress = new PrintRecipient("Megacorp", new NorwegianAddress("0105", "Oslo"));

        when(api.getSenderInformation(any(MayHaveSender.class))).thenReturn(new SenderInformation(1337L, VALID_SENDER,
                asList(
                        DIGIPOST_DELIVERY.withNoParam(), DELIVERY_DIRECT_TO_PRINT.withNoParam(), DELIVERY_DIRECT_TO_PRINT.withNoParam(),
                        PRINTVALIDATION_FONTS.withNoParam(), PRINTVALIDATION_MARGINS_LEFT.withNoParam(), PRINTVALIDATION_PDFVERSION.withNoParam())
        ));

        LOG.debug("Tester direkte til print");
        MessageDeliverer deliverer = new MessageDeliverer(sender);
        Message message = newMessage(messageId, printDocument).attachments(printAttachments).printDetails(new PrintDetails(recipient, returnAddress)).build();

        SendableForPrintOnly sendable = deliverer
                .createPrintOnlyMessage(message)
                .addContent(message.primaryDocument, pdf20Pages());
        for (Document attachment : printAttachments) {
            sendable.addContent(attachment, printablePdf1Page());
        }
        MessageDelivery delivery = sendable.send();
        assertThat(delivery.getStatus(), is(DELIVERED_TO_PRINT));
        then(pdfValidator).should(times(2)).validate(any(byte[].class), any(PdfValidationSettings.class));
        reset(pdfValidator);
    }

    private Message lagDefaultForsendelse() {
        return lagEnkeltForsendelse("emne", UUID.randomUUID().toString(), "12345678900");
    }

    private Message lagEnkeltForsendelse(final String subject, final String messageId, final String fnr) {
        return newMessage(messageId, new Document(UUID.randomUUID().toString(), subject, FileType.PDF, null, new SmsNotification(), null, PASSWORD, NORMAL))
                .personalIdentificationNumber(new PersonalIdentificationNumber(fnr))
                .build();
    }





    public static class StatusLineMock implements StatusLine {

        private final int statusCode;
        public StatusLineMock(int statusCode){
            this.statusCode = statusCode;
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return null;
        }

        @Override
        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getReasonPhrase() {
            return null;
        }
    }
}