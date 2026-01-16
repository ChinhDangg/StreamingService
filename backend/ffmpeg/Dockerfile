FROM linuxserver/ffmpeg:amd64-version-8.0-cli

ARG USER_NAME
ARG USER_ID
ARG GROUP_ID

RUN groupadd -g ${GROUP_ID} ${USER_NAME} || true && \
    useradd -l -u ${USER_ID} -g ${GROUP_ID} -m ${USER_NAME} || true

USER ${USER_NAME}