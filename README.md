# Rides

La aplicación Rides (Mapas) fue desarrollada en Android Studio utilizando Java, con el objetivo de trazar una ruta desde un punto inicial hasta un punto final con ciertas paradas durante el trayecto. 

Este proyecto surge de la necesidad de organizar una ruta donde un conductor pone a disposición su vehículo para pasar por amigos que viven cerca, con esto se dibuja una ruta para ver el orden en el que debe pasar por ellos. La aplicación permite tener dos tipos de usuarios: pasajero y conductor: el usuario pasajero será quien desea que pasen por él y el usuario conductor será quien pase por los pasajeros. 

### Cómo usar el código fuente (Rides source)

El repositorio contiene los archivos Java y XML más importantes del proyecto. Para ejecutar la aplicación, debes:

1. Crear un proyecto de [Google Cloud Console](https://console.cloud.google.com/?pli=1) para obtener una **API Key**.
2. Configurar la **API Key** como se menciona el el pdf en la sección de **Desarrollo experimental**. 
3. Abrir **Android Studio**. 
4. Crear un nuevo proyecto de tipo **Empty Activity**, seleccionando **Java** como lenguaje de programación.
5. Reemplazar o copiar los archivos proporcionados en este repositorio dentro de las carpetas correspondientes del nuevo proyecto:
    * Archivos `.java`  → en `app/src/main/java/tu/paquete/`
    * Archivos `.xml` → en `app/src/main/res/layout/` (o según corresponda)
6. Asegurarte de tener configurado el archivo `AndroidManifest.xml` correctamente (puedes usar el incluido o ajustarlo según tu estructura).
7. Agrega tu **API Key** en los archivos `DriverActivity.java`, `UserActivity.java`, `AndroidManifest.xml` donde aparece _your-api-key_. 
8. Ejecutar el proyecto en un emulador o dispositivo físico.

### APK 

En este repositorio también se incluye el archivo APK (rides.apk) listo para instalar. Puedes usarlo para probar directamente la aplicación en un dispositivo Android sin necesidad de compilar el proyecto.

Cómo instalar el APK:

1. Copia el archivo `.apk` a tu dispositivo Android.
2. Abre el archivo APK en tu dispositivo y sigue las instrucciones para completar la instalación.
3. Es posible que te pida analizar la aplicación, dale **Aceptar** y espera a que lo haga, después de esto se instalará. 

> Este APK es funcional y representa la versión estable del proyecto desarrollada en Android Studio con Java.


