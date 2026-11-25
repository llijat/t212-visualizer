package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.model.CompanyProfile;
import com.llijat.t212visualizer.repository.CompanyProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc sends fake HTTP requests to the controller, without starting a server. */
@ExtendWith(MockitoExtension.class)
class LogoControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock
    private CompanyProfileRepository repository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LogoController(repository)).build();
    }

    @Test
    void aCompanyWithLogo_getsTheImage_withItsType_cachedForAWeek_andNoSniffing() throws Exception {
        CompanyProfile profile = new CompanyProfile("MU_US_EQ");
        profile.updateProfile("Micron Technology, Inc.", PNG, "image/png", Instant.now(), null);
        when(repository.findById("MU_US_EQ")).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/logos/MU_US_EQ"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "max-age=604800"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void aCompanyWithoutLogo_gets404() throws Exception {
        CompanyProfile profile = new CompanyProfile("XYZ_US_EQ");
        profile.updateProfile("Xyz Corp", null, null, Instant.now(), null);
        when(repository.findById("XYZ_US_EQ")).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/logos/XYZ_US_EQ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownCompany_gets404() throws Exception {
        when(repository.findById("NOPE_US_EQ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/logos/NOPE_US_EQ"))
                .andExpect(status().isNotFound());
    }
}
