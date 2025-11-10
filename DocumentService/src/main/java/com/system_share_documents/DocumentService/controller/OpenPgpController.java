package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/openpgp")
public class OpenPgpController {

    @Autowired
    private OpenPgpService openPgpService;

    @PostMapping("/sign")
    public ApiResponse<Map<String, String>> signDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("privateKey") MultipartFile privateKeyFile,
            @RequestParam(value = "passphrase", required = false) String passphrase
    ) throws Exception {
        String privateKeyArmored = new String(privateKeyFile.getBytes());
        byte[] signature = openPgpService.signDetached(
                file.getBytes(),
                privateKeyArmored,
                passphrase == null ? new char[]{} : passphrase.toCharArray()
        );

        String base64Signature = Base64.getEncoder().encodeToString(signature);

        return ApiResponse.success(
                "OK",
                "Sign document successfully",
                Map.of("signature", base64Signature)
        );
    }


}
