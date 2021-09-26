package com.mvanniekerk.akka.compute.util;

import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.ExceptionWithErrorInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class HttpObjectMapper {

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER =
            new ObjectMapper().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

    public static Unmarshaller<HttpEntity, JsonNode> genericJsonUnmarshaller() {
        return Unmarshaller.forMediaType(MediaTypes.APPLICATION_JSON, Unmarshaller.entityToString())
                .thenApply(s -> {
                    try {
                        return DEFAULT_OBJECT_MAPPER.readTree(s);
                    } catch (IOException e) {
                        throw new JacksonUnmarshallingException(e);
                    }
                });
    }

    public static class JacksonUnmarshallingException extends ExceptionWithErrorInfo {
        public JacksonUnmarshallingException(IOException cause) {
            super(new ErrorInfo("Cannot unmarshal JSON", cause.getMessage()), cause);
        }
    }
}
