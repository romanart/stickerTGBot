CREATE DATABASE IF NOT EXISTS stickerBotDataBase;

USE stickerBotDataBase;

CREATE TABLE IF NOT EXISTS userState (
    user_id   BIGINT   NOT NULL,
    chat_id   BIGINT   NOT NULL,
    state_id  SMALLINT NOT NULL,
    PRIMARY KEY (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS stickerPackOwner(
    sticker_pack_id  VARCHAR(512) NOT NULL UNIQUE,
    owner_id BIGINT NOT NULL,
    PRIMARY KEY (sticker_pack_id)
);

CREATE TABLE IF NOT EXISTS groupStickerPack(
   chat_id   BIGINT   NOT NULL UNIQUE ,
#    pack_owner_id BIGINT NOT NULL ,
   sticker_pack_name  VARCHAR(1000) NOT NULL,
   PRIMARY KEY (chat_id)
);

CREATE TABLE IF NOT EXISTS userCurrentStickerPack (
    user_id   BIGINT   NOT NULL,
    chat_id   BIGINT   NOT NULL,
    sticker_pack_id  VARCHAR(1000) NOT NULL,
    PRIMARY KEY (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS userStickerPacks (
    user_id   BIGINT   NOT NULL,
    chat_id   BIGINT   NOT NULL,
    sticker_pack_id  VARCHAR(1000) NOT NULL,
    PRIMARY KEY (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS defaultUserEmojy (
    user_id   BIGINT   NOT NULL UNIQUE,
    emojy  VARCHAR(1) CHARACTER SET utf8mb4 NOT NULL,
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS chatWelcomeMessage (
    chat_id   BIGINT   NOT NULL UNIQUE ,
    welcome_message  VARCHAR(1000) NOT NULL,
    PRIMARY KEY (chat_id)
);

CREATE TABLE IF NOT EXISTS chatUserCaption (
    chat_id   BIGINT   NOT NULL,
    user_id   BIGINT   NOT NULL,
    welcome_message  VARCHAR(1000) NOT NULL,
    CONSTRAINT uniqChatUser UNIQUE (chat_id, user_id)
);

CREATE TABLE IF NOT EXISTS hogwartsGameRole (
  user_id BIGINT NOT NULL,
  chat_id BIGINT NOT NULL,
  team_id INT NOT NULL,
  snitch_score INT NOT NULL,
  prihod_score INT NOT NULL,

  last_stitch_time BIGINT NOT NULL,
  last_prihod_time BIGINT NOT NULL,

  PRIMARY KEY (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS hogwartsStats (
  chat_id BIGINT NOT NULL UNIQUE,

  gryffindor_score INT NOT NULL,
  ravenclaw_score INT NOT NULL,
  hufflepuff_score INT NOT NULL,
  slythering_score INT NOT NULL,

  last_success VARCHAR(100) NOT NULL ,

  PRIMARY KEY (chat_id)
);

CREATE TABLE IF NOT EXISTS hogwartsPlayerNickname (
  user_id BIGINT NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,

  PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS hogwartsCheat (
  user_id BIGINT NOT NULL UNIQUE,
  magic_value BIGINT NOT NULL,
  time_stamp BIGINT NOT NULL,

  PRIMARY KEY (user_id)
);
