package io.github.eliaslameiro.galetube.video.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration 
@EnableConfigurationProperties(VideoProperties.class) 
public class VideoConfig {
}
