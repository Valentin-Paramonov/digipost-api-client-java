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
package no.digipost.api.client.eksempelkode;

import no.digipost.api.client.DigipostClientConfig;
import no.digipost.api.client.delivery.ApiFlavor;

import no.digipost.api.client.DigipostClient;
import no.digipost.api.client.representations.Suggestion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import static no.digipost.api.client.DigipostClientConfig.DigipostClientConfigBuilder.newBuilder;

/**
 * Kode som brukes i dokumentasjonen for klientbiblioteket.
 *
 */
@SuppressWarnings("unused")
public class AutocompleteEksempel {
	// Din virksomhets Digipost-kontoid
	private static final long AVSENDERS_KONTOID = 10987;

	// Passordet sertifikatfilen er beskyttet med
	private static final String SERTIFIKAT_PASSORD = "SertifikatPassord123";

	public static void main(final String[] args) {

		// 1. Vi leser inn sertifikatet du har knyttet til din Digipost-konto (i
		// .p12-formatet)
		InputStream sertifikatInputStream = lesInnSertifikat();

		// 2. Vi oppretter en DigipostClient
		DigipostClient client = new DigipostClient(newBuilder().build(), ApiFlavor.STEPWISE_REST, "https://api.digipost.no", AVSENDERS_KONTOID, sertifikatInputStream, SERTIFIKAT_PASSORD);

		// 3. Vi ber om forslag til autofullføring
		List<Suggestion> suggestions = client.getAutocompleteSuggestions("Gunn").getSuggestions();

	}

	private static InputStream getMessageContent() {
		// Her må du returnere brevinnholdet du ønsker å sende istedenfor null
		return null;
	}

	private static InputStream lesInnSertifikat() {
		try {
			// Leser inn sertifikatet
			return new FileInputStream(new File("/path/til/sertifikatfil.p12"));
		} catch (FileNotFoundException e) {
			// Håndter at sertifikatet ikke kunne leses!
			throw new RuntimeException("Kunne ikke lese sertifikatfil: " + e.getMessage(), e);
		}
	}
}