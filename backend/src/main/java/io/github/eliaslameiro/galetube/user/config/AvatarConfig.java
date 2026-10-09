package io.github.eliaslameiro.galetube.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration 
@EnableConfigurationProperties(AvatarProperties.class)
public class AvatarConfig {
}
