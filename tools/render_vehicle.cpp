// Offline GLB inspection with the same Filament release as Android. No emulator, server or UI.
#include <filament/Engine.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/View.h>
#include <filament/Viewport.h>
#include <filament/Camera.h>
#include <filament/SwapChain.h>
#include <filament/Skybox.h>
#include <filament/IndirectLight.h>
#include <filament/LightManager.h>
#include <filament/RenderableManager.h>
#include <filament/TransformManager.h>
#include <cmath>
#include <gltfio/AssetLoader.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/MaterialProvider.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/materials/uberarchive.h>
#include <image/Ktx1Bundle.h>
#include <ktxreader/Ktx1Reader.h>
#include <utils/EntityManager.h>
#include <utils/NameComponentManager.h>
#include <backend/PixelBufferDescriptor.h>
#include <fstream>
#include <vector>
#include <string>
#include <cstdio>
#include <cstdlib>
#include <iostream>
using namespace filament;
using namespace filament::gltfio;
static std::vector<uint8_t> read(const char* path) { std::ifstream f(path,std::ios::binary);return std::vector<uint8_t>((std::istreambuf_iterator<char>(f)),{}); }
struct Capture {std::string path;int width,height;};
int main(int argc,char**argv) {
 if(argc<5) {std::cerr<<"Usage: render_vehicle model.glb environment_ibl.ktx output-prefix ground.glb\n";return 1;}
 const bool cockpitMode=argc>=8;
 constexpr int width=1600,height=1000;
 Engine* engine=Engine::create(backend::Backend::METAL);
 auto* swap=engine->createSwapChain(width,height,SwapChain::CONFIG_READABLE);
 auto* renderer=engine->createRenderer();auto* scene=engine->createScene();auto* view=engine->createView();
 auto cameraEntity=utils::EntityManager::get().create();auto* camera=engine->createCamera(cameraEntity);
 view->setScene(scene);view->setCamera(camera);view->setViewport({0,0,width,height});
 view->setAntiAliasing(View::AntiAliasing::FXAA);
 auto ao=view->getAmbientOcclusionOptions();ao.enabled=true;ao.radius=.35f;ao.power=1.2f;view->setAmbientOcclusionOptions(ao);
 auto taa=view->getTemporalAntiAliasingOptions();taa.enabled=true;view->setTemporalAntiAliasingOptions(taa);
 camera->setProjection(28.,double(width)/height,.04,100.,Camera::Fov::VERTICAL);camera->setExposure(16.f,1.f/125.f,100.f);
 auto* sky=Skybox::Builder().color({.85f,.87f,.9f,1.f}).build(*engine);scene->setSkybox(sky);
 auto env=read(argv[2]);auto* bundle=new image::Ktx1Bundle(env.data(),uint32_t(env.size()));
 math::float3 sh[9]={};if(!bundle->getSphericalHarmonics(sh))sh[0]={.78f,.78f,.78f};
 auto* reflections=ktxreader::Ktx1Reader::createTexture(engine,bundle,false);
 auto* ibl=IndirectLight::Builder().reflections(reflections).irradiance(3,sh).intensity(30000.f).build(*engine);scene->setIndirectLight(ibl);
 auto sun=utils::EntityManager::get().create();LightManager::ShadowOptions shadows;shadows.mapSize=2048;shadows.shadowFar=15.f;shadows.normalBias=.4f;shadows.shadowBulbRadius=.15f;
 view->setShadowType(View::ShadowType::PCSS);
 LightManager::Builder(LightManager::Type::DIRECTIONAL).direction({-.6f,-1.f,-.3f}).color({1.f,.98f,.94f}).intensity(85000.f).castShadows(true).shadowOptions(shadows).build(*engine,sun);scene->addEntity(sun);
 auto* provider=createUbershaderProvider(engine,UBERARCHIVE_DEFAULT_DATA,UBERARCHIVE_DEFAULT_SIZE);
 utils::NameComponentManager namesManager(utils::EntityManager::get());
 auto* loader=AssetLoader::create({engine,provider,&namesManager});auto* resources=new ResourceLoader({engine,nullptr,true});
 std::vector<FilamentAsset*> assets;
 std::vector<int> inputs={1,4};if(cockpitMode)inputs.push_back(5);
 for(int i: inputs) {
  auto bytes=read(argv[i]);auto* asset=loader->createAsset(bytes.data(),uint32_t(bytes.size()));if(!asset||!resources->loadResources(asset)){std::cerr<<"load failed\n";return 2;}
  if(cockpitMode && i==1) {
   for(size_t n=0;n<asset->getEntityCount();n++) {
    auto entity=asset->getEntities()[n];const char* rawName=asset->getName(entity);std::string name=rawName?rawName:"";
    bool hidden=name=="windshield" || name=="rear_window";
    for(auto prefix:{"side_glass","dash","vent","instrument","center_","steering_","door_card","cabin_floor","room_mirror","interior_","mirror_surface"})if(name.rfind(prefix,0)==0)hidden=true;
    if(!hidden)scene->addEntity(entity);
   }
  } else scene->addEntities(asset->getEntities(),asset->getEntityCount());
  if(i==5)engine->getTransformManager().setTransform(engine->getTransformManager().getInstance(asset->getRoot()),math::mat4f::translation(math::float3{float(std::atof(argv[6])),float(std::atof(argv[7])),0.f}));
  if(i==4)for(size_t n=0;n<asset->getRenderableEntityCount();n++){auto instance=engine->getRenderableManager().getInstance(asset->getRenderableEntities()[n]);engine->getRenderableManager().setCastShadows(instance,false);}
  asset->releaseSourceData();assets.push_back(asset);
 }
 if(cockpitMode && argc>=9) {
  auto bytes=read(argv[8]);
  for(float z:{-5.5f,0.f,5.5f}) {
   auto* parked=loader->createAsset(bytes.data(),uint32_t(bytes.size()));
   if(!parked || !resources->loadResources(parked))return 3;
   auto& tm=engine->getTransformManager();
   tm.setTransform(tm.getInstance(parked->getRoot()),math::mat4f::translation(math::float3{12.f,0.f,z})*math::mat4f::rotation(3.141592653589793f,math::float3{0,1,0}));
   scene->addEntities(parked->getEntities(),parked->getEntityCount());
   parked->releaseSourceData();assets.push_back(parked);
  }
 }
 const math::double3 target={1.4175,.70,0};
 const math::double3 offsets[]={{5.4,1.05,-6.6},{0,.7,-7.8},{-5.3,1.25,-6.5}};const char* names[]={"front",cockpitMode?"left":"side",cockpitMode?"right":"rear"};
 for(int pose=0;pose<3;pose++) {
  if(cockpitMode) {
   camera->setProjection(64.,double(width)/height,.025,100.,Camera::Fov::VERTICAL);
   const math::double3 eye={std::atof(argv[6]),std::atof(argv[7]),-.37};
   double yaw=pose==0?0:pose==1?-1.05:1.05,pitch=5.*3.141592653589793/180.;
   camera->lookAt(eye,eye+math::double3{std::cos(yaw)*std::cos(pitch),-std::sin(pitch),std::sin(yaw)*std::cos(pitch)},{0,1,0});
  } else camera->lookAt(target+offsets[pose],target,{0,1,0});
  for(int frame=0;frame<20;frame++) {
   if(!renderer->beginFrame(swap)){--frame;continue;}
   renderer->render(view);
   if(frame==19) {
    auto* capture=new Capture{std::string(argv[3])+"-"+names[pose]+".ppm",width,height};
    const size_t size=width*height*4;
    backend::PixelBufferDescriptor buffer(new uint8_t[size],size,backend::PixelDataFormat::RGBA,backend::PixelDataType::UBYTE,
      [](void*data,size_t,void*user){auto*c=static_cast<Capture*>(user);auto*p=static_cast<uint8_t*>(data);FILE*f=std::fopen(c->path.c_str(),"wb");std::fprintf(f,"P6\n%d %d\n255\n",c->width,c->height);for(int y=0;y<c->height;y++)for(int x=0;x<c->width;x++)std::fwrite(p+4*(y*c->width+x),1,3,f);std::fclose(f);std::cout<<c->path<<"\n";delete[]p;delete c;},capture);
    renderer->readPixels(0,0,width,height,std::move(buffer));
   }
   renderer->endFrame();engine->flushAndWait();
  }
 }
 engine->flushAndWait();
 for(auto*asset:assets){scene->removeEntities(asset->getEntities(),asset->getEntityCount());loader->destroyAsset(asset);}
 delete resources;AssetLoader::destroy(&loader);provider->destroyMaterials();delete provider;
 engine->destroy(view);engine->destroyCameraComponent(cameraEntity);utils::EntityManager::get().destroy(cameraEntity);
 scene->setIndirectLight(nullptr);scene->setSkybox(nullptr);engine->destroy(ibl);engine->destroy(reflections);engine->destroy(sky);engine->destroy(sun);utils::EntityManager::get().destroy(sun);
 engine->destroy(scene);engine->destroy(renderer);engine->destroy(swap);Engine::destroy(&engine);
}
