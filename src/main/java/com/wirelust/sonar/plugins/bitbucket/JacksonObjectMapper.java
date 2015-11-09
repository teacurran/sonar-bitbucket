package com.wirelust.sonar.plugins.bitbucket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

/**
 * Date: 07-Oct-2015
 *
 * @author T. Curran
 */
public class JacksonObjectMapper extends ObjectMapper {

	public static JacksonObjectMapper get() {

		JacksonObjectMapper mapper = new JacksonObjectMapper();

		mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);

		// Bitbucket uses underscores in their json names, our value objects use camel case
		mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

		// Bitbucket will sometimes return an array and sometimes return a single object for most lists
		mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

		// In the unit tests this is set to true because we want it to fail there
		// but in production we don't want it to fail so easily.
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		return mapper;
	}
}
