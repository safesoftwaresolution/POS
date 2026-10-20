package com.sciencebot.pos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class Application {

	private static final Logger log = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(Application.class, args);
	}

	private static void loadDotEnv() {
		List<Path> candidatePaths = List.of(
				Path.of(".env"),
				Path.of("backend/.env"),
				Path.of("../.env")
		);

		for (Path path : candidatePaths) {
			if (Files.exists(path) && Files.isRegularFile(path)) {
				try {
					List<String> lines = Files.readAllLines(path);
					for (String line : lines) {
						String trimmed = line.trim();
						if (trimmed.isEmpty() || trimmed.startsWith("#")) {
							continue;
						}
						int eqIdx = trimmed.indexOf('=');
						if (eqIdx > 0) {
							String key = trimmed.substring(0, eqIdx).trim();
							String value = trimmed.substring(eqIdx + 1).trim();
							if ((value.startsWith("\"") && value.endsWith("\"")) ||
								(value.startsWith("'") && value.endsWith("'"))) {
								value = value.substring(1, value.length() - 1);
							}
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, value);
							}
						}
					}
					log.info("Variables cargadas exitosamente desde .env ({})", path);
					break;
				} catch (IOException ex) {
					log.warn("No se pudo leer el archivo .env en {}: {}", path, ex.getMessage());
				}
			}
		}
	}

}
