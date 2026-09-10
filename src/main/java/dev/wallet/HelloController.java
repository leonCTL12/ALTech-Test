package dev.wallet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	private record HelloResponse(String message) {
	}

	@GetMapping("/api/hello")
	public HelloResponse hello() {
		return new HelloResponse("Hello from Wallet Ledger!");
	}

}