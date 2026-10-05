package com.example.payment_service.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ThrowingController.class)
@Import({GlobalExceptionHandler.class, ThrowingController.class})
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void invalidBodyReturns400ProblemDetail() throws Exception {
		mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Помилка валідації"))
				.andExpect(jsonPath("$.detail").value("Некоректні дані запиту"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void malformedJsonReturns400ProblemDetail() throws Exception {
		mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Некоректний запит"))
				.andExpect(jsonPath("$.detail").value("Не вдалося прочитати тіло запиту"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void missingHeaderReturns400ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/header"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Відсутній заголовок"))
				.andExpect(jsonPath("$.detail").value("Відсутній необхідний заголовок «X-Required»"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void unknownUrlReturns404ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Не знайдено"))
				.andExpect(jsonPath("$.detail").value("Ресурс test/does-not-exist не знайдено"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void wrongMethodReturns405ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/validation"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.title").value("Метод не дозволено"))
				.andExpect(jsonPath("$.detail").value("Метод «GET» не підтримується"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void unexpectedExceptionReturns500ProblemDetailWithoutLeakingMessage() throws Exception {
		mockMvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.title").value("Внутрішня помилка сервера"))
				.andExpect(jsonPath("$.detail").value("Сталася непередбачена помилка"))
				.andExpect(jsonPath("$.detail").value(not(containsString("secret"))))
				.andExpect(jsonPath("$.timestamp").exists());
	}
}
