package com.example.imageprocesspip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

@SpringBootApplication
public class ImageProcessPipApplication {

	public static void main(String[] args) throws Exception{
		SpringApplication.run(ImageProcessPipApplication.class, args);
		//convertimg();
	}

	public static void convertimg() throws Exception{

		var img = "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";
		Files.write(Paths.get("C:\\Users\\obest\\Downloads\\temp\\test1.jpg"), Base64.getDecoder().decode(img), StandardOpenOption.CREATE_NEW);
	}

}
