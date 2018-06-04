FROM debian:stretch

RUN apt-get update
RUN apt-get -y install \
        mingw-w64 \
        make

COPY . /windows/

RUN cd /windows && make -f Makefile.mingw
