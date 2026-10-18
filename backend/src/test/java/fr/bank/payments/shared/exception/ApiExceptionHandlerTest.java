// createdBy ELMANSOURI Mohamed
package fr.bank.payments.shared.exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fr.bank.payments.messages.application.port.in.ConsultMessagesUseCase;
import fr.bank.payments.messages.domain.exception.MessageNotFoundException;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.MessageRestAdapter;
import fr.bank.payments.messages.infrastructure.adapter.in.rest.mapper.MessageResponseMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiExceptionHandlerTest {
  private final ConsultMessagesUseCase messages = mock(ConsultMessagesUseCase.class);
  private MockMvc mvc;

  @BeforeEach
  void configure() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new MessageRestAdapter(messages, Mappers.getMapper(MessageResponseMapper.class)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "limit=0",
        "status=UNKNOWN",
        "beforeId=abc",
        "from=2026-10-17T12:00:00Z&to=2026-10-17T12:00:00Z"
      })
  void invalidParametersHaveAConsistentProblemBody(String query) throws Exception {
    mvc.perform(get("/api/messages?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").isNotEmpty())
        .andExpect(jsonPath("$.detail").isNotEmpty());
    verifyNoInteractions(messages);
  }

  @Test
  void missingMessageIsHandledByTheAdvice() throws Exception {
    when(messages.findById(42)).thenThrow(new MessageNotFoundException(42));
    mvc.perform(get("/api/messages/42"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Message introuvable"));
  }

  @Test
  void persistenceFailureIsSanitizedAndIncludesRetryAfter() throws Exception {
    when(messages.search(any())).thenThrow(new DataAccessResourceFailureException("jdbc:password"));
    mvc.perform(get("/api/messages"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(header().string("Retry-After", "5"))
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
  }

  @Test
  void unexpectedFailuresAreNotReportedAsBadUserInput() throws Exception {
    when(messages.search(any()))
        .thenThrow(new IllegalArgumentException("confidential internal error"));
    mvc.perform(get("/api/messages"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.detail").value("Une erreur inattendue est survenue."))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("confidential"))));
  }

  @Test
  void optimisticLockFailureIsAConflictInsteadOfAStorageOutage() throws Exception {
    when(messages.search(any()))
        .thenThrow(new OptimisticLockingFailureException("internal stale version"));
    mvc.perform(get("/api/messages"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Modification concurrente"))
        .andExpect(header().doesNotExist("Retry-After"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal"))));
  }
}
