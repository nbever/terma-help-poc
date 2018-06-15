FROM node:8.11.1

ENV PUBLIC_CONTENT /opt/termahelp
ENV PORT 8083

COPY index.js /help-server/
COPY package.json /help-server/

WORKDIR /help-server

RUN npm install 

EXPOSE ${PORT} 
CMD ["node", "./index.js", "${PORT}", "${PUBLIC_CONTENT}"]