FROM serversideup/php:8.3-fpm-nginx AS production

WORKDIR /var/www/html

USER root
COPY --chown=www-data:www-data . .

RUN composer install --no-dev --optimize-autoloader --no-interaction --no-progress \
    && mkdir -p storage/framework/sessions storage/framework/views \
       storage/framework/cache/data storage/app/public storage/logs \
    && chown -R www-data:www-data storage bootstrap/cache

USER www-data

ENV APP_ENV=production
ENV LOG_CHANNEL=stderr
