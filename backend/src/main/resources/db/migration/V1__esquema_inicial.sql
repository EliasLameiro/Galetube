CREATE TABLE users (
    id         BINARY(16)   NOT NULL,
    username   VARCHAR(30)  NOT NULL,
    email      VARCHAR(254) NOT NULL,
    password   VARCHAR(72)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE videos (
    id          BINARY(16)    NOT NULL,
    owner_id    BINARY(16)    NOT NULL,
    title       VARCHAR(100)  NOT NULL,
    description VARCHAR(5000) NULL,
    visibility  VARCHAR(20)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_videos_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;