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
package no.digipost.api.client;

import static no.digipost.api.client.Headers.X_Digipost_UserId;
import static no.digipost.api.client.representations.MediaTypes.DIGIPOST_MEDIA_TYPE_V1;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import javax.ws.rs.core.MediaType;

import no.digipost.api.client.DigipostClientException.ErrorType;
import no.digipost.api.client.representations.Autocomplete;
import no.digipost.api.client.representations.EntryPoint;
import no.digipost.api.client.representations.ErrorMessage;
import no.digipost.api.client.representations.Link;
import no.digipost.api.client.representations.MediaTypes;
import no.digipost.api.client.representations.Message;
import no.digipost.api.client.representations.Recipients;

import org.apache.commons.io.IOUtils;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.ClientFilter;

/**
 * Denne klassen tar seg av de enkelte HTTP-forespørslene man kan gjøre mot
 * REST-API-et, nemlig:
 * 
 * <ul>
 * <li>Hente søkeforslag (autocomplete)</li>
 * <li>Søke etter mottakere</li>
 * <li>Opprette en forsendelsesressurs på serveren
 * <li>Hente en allerede opprettet forsendelsesressurs fra serveren
 * <li>Sende innholdet (PDF) for en allerede opprettet forsendelsesressurs til
 * serveren, og dermed sende brevet til mottakeren
 * <ul>
 * 
 * For å sende et brev gjennom Digipost er det tilstrekkelig å gjøre disse to
 * kallene:
 * 
 * <pre>
 * createMessage(message);
 * addToContentAndSend(createdMessage, content);
 * </pre>
 * 
 * Dette kan også gjøres ved å kalle metoden {@code sendMessage} i klassen
 * {@code MessageSender}, som i tillegg gjør en del feilhåndtering.
 */
public class ApiService {

	private static final String ENTRY_POINT = "/";
	private final WebResource webResource;
	private final long senderAccountId;

	private EntryPoint cachedEntryPoint;

	public ApiService(final WebResource webResource, final long senderAccountId) {
		this.webResource = webResource;
		this.senderAccountId = senderAccountId;
	}

	public ClientResponse getEntryPoint() {
		return webResource
				.path(ENTRY_POINT)
				.accept(DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.get(ClientResponse.class);
	}

	/**
	 * Oppretter en ny forsendelsesressurs på serveren ved å sende en
	 * POST-forespørsel.
	 */
	public ClientResponse createMessage(final Message message) {
		EntryPoint entryPoint = getCachedEntryPoint();
		return webResource
				.path(entryPoint.getCreateMessageUri().getPath())
				.accept(DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.type(DIGIPOST_MEDIA_TYPE_V1)
				.post(ClientResponse.class, message);
	}

	/**
	 * Henter en allerede eksisterende forsendelsesressurs fra serveren.
	 */
	public ClientResponse fetchExistingMessage(final URI location) {
		return webResource
				.path(location.getPath())
				.accept(DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.get(ClientResponse.class);
	}

	/**
	 * Angir innholdet i en allerede opprettet forsendelse og sender det som en
	 * POST-forespørsel til serveren.
	 * 
	 * OBS! Denne metoden fører til at brevet blir sendt på ordentlig.
	 * 
	 * Før man kaller denne metoden, må man allerede ha opprettet en
	 * forsendelsesressurs på serveren ved metoden {@code opprettForsendelse}.
	 */
	public ClientResponse addToContentAndSend(final Message createdMessage, final InputStream letterContent) {
		Link addFileLink = fetchAddFileLink(createdMessage);

		byte[] content = readLetterContent(letterContent);

		return webResource
				.path(addFileLink.getUri().getPath())
				.accept(DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.type(MediaType.APPLICATION_OCTET_STREAM)
				.post(ClientResponse.class, content);

	}

	private Link fetchAddFileLink(final Message createdMessage) {
		Link addContentLink = createdMessage.getFileLink();
		if (addContentLink == null) {
			throw new DigipostClientException(ErrorType.PROBLEM_WITH_REQUEST,
					"Kan ikke legge til innhold til en forsendelse som ikke har en link for å gjøre dette.");
		}
		return addContentLink;
	}

	private byte[] readLetterContent(final InputStream letterContent) {
		try {
			return IOUtils.toByteArray(letterContent);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Recipients search(final String searchString) {
		return webResource
				.path(getCachedEntryPoint().getSearchUri().getPath() + "/" + searchString)
				.accept(DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.get(Recipients.class);
	}

	public Autocomplete searchSuggest(final String searchString) {
		return webResource
				.path(getCachedEntryPoint().getAutocompleteUri().getPath() + "/" + searchString)
				.accept(MediaTypes.DIGIPOST_MEDIA_TYPE_V1)
				.header(X_Digipost_UserId, senderAccountId)
				.get(Autocomplete.class);
	}

	public void addFilter(final ClientFilter filter) {
		webResource.addFilter(filter);
	}

	private EntryPoint getCachedEntryPoint() {
		if (cachedEntryPoint == null) {
			ClientResponse response = getEntryPoint();
			if (response.getStatus() != ClientResponse.Status.OK.getStatusCode()) {
				throw new DigipostClientException(ErrorType.GENERAL_ERROR, response.getEntity(ErrorMessage.class).getErrorMessage());
			} else {
				cachedEntryPoint = response.getEntity(EntryPoint.class);
			}
		}
		return cachedEntryPoint;
	}
}