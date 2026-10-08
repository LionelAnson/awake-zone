#define NOMINMAX
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d11.h>
#include <dxgi1_3.h>
#include <d2d1_1.h>
#include <d2d1effects.h>
#include <dcomp.h>
#include <windows.graphics.capture.interop.h>
#include <windows.graphics.directx.direct3d11.interop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Graphics.Capture.h>
#include <winrt/Windows.Graphics.DirectX.h>
#include <winrt/Windows.Graphics.DirectX.Direct3D11.h>
#include <winrt/Windows.UI.h>
#include <atomic>
#include <thread>
#include <mutex>
#include <future>
#include <memory>
#include <algorithm>
#include <cmath>

namespace wc=winrt::Windows::Graphics::Capture;
namespace wd=winrt::Windows::Graphics::DirectX;
using winrt::com_ptr;
using winrt::check_hresult;
namespace {
std::mutex lifecycle;
std::thread worker;
std::atomic<bool> stopping{false};
std::atomic<int> error{0};
std::atomic<double> luminance{-1};
std::atomic<unsigned long long> frames{0};
// -2: not requested, -1: pending, -3: API error; 0..4: AppCapabilityAccessStatus.
std::atomic<int> borderAccess{-2},borderRequired{-1},borderError{0};
winrt::fire_and_forget requestBorderless(){
    try{
        auto access=co_await wc::GraphicsCaptureAccess::RequestAccessAsync(wc::GraphicsCaptureAccessKind::Borderless);
        borderAccess.store(static_cast<int>(access));
    }catch(winrt::hresult_error const& e){borderError=int(e.code());borderAccess=-3;}
    catch(...){borderError=E_FAIL;borderAccess=-3;}
}
HWND activeWindow=nullptr;
constexpr int pad=24;

struct Renderer {
    HWND hwnd;
    std::shared_ptr<void> signal{CreateEventW(nullptr,FALSE,FALSE,nullptr),[](void* h){if(h)CloseHandle(h);}};
    HANDLE arrived=signal.get();
    com_ptr<ID3D11Device> device;
    com_ptr<ID3D11DeviceContext> context;
    com_ptr<IDXGISwapChain1> swap;
    com_ptr<ID2D1DeviceContext> d2;
    com_ptr<ID2D1Bitmap1> target,input;
    com_ptr<ID2D1Effect> blur;
    com_ptr<ID3D11Texture2D> texture,readback;
    com_ptr<IDCompositionDevice> composition;
    com_ptr<IDCompositionTarget> compositionTarget;
    com_ptr<IDCompositionVisual> visual;
    wc::Direct3D11CaptureFramePool pool{nullptr};
    wc::GraphicsCaptureSession session{nullptr};
    winrt::event_token token{};
    HMONITOR monitor=nullptr;
    RECT monitorRect{};
    int width=0,height=0,captureWidth=0,captureHeight=0;
    ULONGLONG sampled=0;
    bool borderConfigured=false;
    explicit Renderer(HWND h):hwnd(h){if(!arrived)winrt::throw_last_error();}
    void closeCapture(){
        if(pool)pool.FrameArrived(token);
        if(session)session.Close();
        if(pool)pool.Close();
        session=nullptr;pool=nullptr;monitor=nullptr;borderConfigured=false;borderRequired=-1;
    }
    void configureBorder(){
        using Access=winrt::Windows::Security::Authorization::AppCapabilityAccess::AppCapabilityAccessStatus;
        if(!session||borderConfigured||borderAccess.load()!=static_cast<int>(Access::Allowed))return;
        borderConfigured=true;
        try{session.IsBorderRequired(false);borderRequired=session.IsBorderRequired()?1:0;}
        catch(winrt::hresult_error const& e){borderError=int(e.code());}
    }
    ~Renderer(){
        try{closeCapture();}catch(...){}
        if(compositionTarget){compositionTarget->SetRoot(nullptr);composition->Commit();}
        if(d2)d2->SetTarget(nullptr);
        // In-flight event callbacks retain their own shared event handle.
    }
    void init(){
        check_hresult(D3D11CreateDevice(nullptr,D3D_DRIVER_TYPE_HARDWARE,nullptr,D3D11_CREATE_DEVICE_BGRA_SUPPORT,nullptr,0,D3D11_SDK_VERSION,device.put(),nullptr,context.put()));
        auto dxgi=device.as<IDXGIDevice>();
        com_ptr<ID2D1Factory1> factory;
        check_hresult(D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED,__uuidof(ID2D1Factory1),nullptr,factory.put_void()));
        com_ptr<ID2D1Device> dev;check_hresult(factory->CreateDevice(dxgi.get(),dev.put()));
        check_hresult(dev->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE,d2.put()));
        check_hresult(DCompositionCreateDevice(dxgi.get(),__uuidof(IDCompositionDevice),composition.put_void()));
        check_hresult(composition->CreateTargetForHwnd(hwnd,FALSE,compositionTarget.put()));
        check_hresult(composition->CreateVisual(visual.put()));
        check_hresult(compositionTarget->SetRoot(visual.get()));
    }
    void resize(int w,int h){
        d2->SetTarget(nullptr);target=nullptr;input=nullptr;blur=nullptr;texture=nullptr;readback=nullptr;
        if(swap){check_hresult(visual->SetContent(nullptr));check_hresult(composition->Commit());swap=nullptr;}
        auto dxgi=device.as<IDXGIDevice>();com_ptr<IDXGIAdapter> adapter;check_hresult(dxgi->GetAdapter(adapter.put()));
        com_ptr<IDXGIFactory2> factory;check_hresult(adapter->GetParent(__uuidof(IDXGIFactory2),factory.put_void()));
        DXGI_SWAP_CHAIN_DESC1 sc{};sc.Width=w;sc.Height=h;sc.Format=DXGI_FORMAT_B8G8R8A8_UNORM;sc.SampleDesc.Count=1;
        sc.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT;sc.BufferCount=2;sc.SwapEffect=DXGI_SWAP_EFFECT_FLIP_SEQUENTIAL;
        sc.AlphaMode=DXGI_ALPHA_MODE_PREMULTIPLIED;sc.Flags=DXGI_SWAP_CHAIN_FLAG_FRAME_LATENCY_WAITABLE_OBJECT;
        check_hresult(factory->CreateSwapChainForComposition(device.get(),&sc,nullptr,swap.put()));
        check_hresult(swap.as<IDXGISwapChain2>()->SetMaximumFrameLatency(1));
        com_ptr<IDXGISurface> surface;check_hresult(swap->GetBuffer(0,__uuidof(IDXGISurface),surface.put_void()));
        auto props=D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_TARGET|D2D1_BITMAP_OPTIONS_CANNOT_DRAW,D2D1::PixelFormat(sc.Format,D2D1_ALPHA_MODE_PREMULTIPLIED),96,96);
        check_hresult(d2->CreateBitmapFromDxgiSurface(surface.get(),&props,target.put()));d2->SetTarget(target.get());
        D3D11_TEXTURE2D_DESC td{};td.Width=w+pad*2;td.Height=h+pad*2;td.MipLevels=td.ArraySize=1;td.Format=sc.Format;td.SampleDesc.Count=1;
        td.Usage=D3D11_USAGE_DEFAULT;td.BindFlags=D3D11_BIND_SHADER_RESOURCE|D3D11_BIND_RENDER_TARGET;
        check_hresult(device->CreateTexture2D(&td,nullptr,texture.put()));auto inputSurface=texture.as<IDXGISurface>();
        auto ip=D2D1::BitmapProperties1(D2D1_BITMAP_OPTIONS_NONE,D2D1::PixelFormat(td.Format,D2D1_ALPHA_MODE_IGNORE),96,96);
        check_hresult(d2->CreateBitmapFromDxgiSurface(inputSurface.get(),&ip,input.put()));
        check_hresult(d2->CreateEffect(CLSID_D2D1GaussianBlur,blur.put()));blur->SetInput(0,input.get());
        check_hresult(blur->SetValue(D2D1_GAUSSIANBLUR_PROP_STANDARD_DEVIATION,6.0f));
        td.Usage=D3D11_USAGE_STAGING;td.BindFlags=0;td.CPUAccessFlags=D3D11_CPU_ACCESS_READ;
        check_hresult(device->CreateTexture2D(&td,nullptr,readback.put()));
        check_hresult(visual->SetContent(swap.get()));check_hresult(composition->Commit());width=w;height=h;
    }
    void capture(HMONITOR m){
        closeCapture();MONITORINFO info{sizeof(info)};if(!GetMonitorInfoW(m,&info))winrt::throw_last_error();monitorRect=info.rcMonitor;
        auto interop=winrt::get_activation_factory<wc::GraphicsCaptureItem,IGraphicsCaptureItemInterop>();wc::GraphicsCaptureItem item{nullptr};
        check_hresult(interop->CreateForMonitor(m,winrt::guid_of<wc::GraphicsCaptureItem>(),winrt::put_abi(item)));
        com_ptr<::IInspectable> wrapped;check_hresult(CreateDirect3D11DeviceFromDXGIDevice(device.as<IDXGIDevice>().get(),wrapped.put()));
        pool=wc::Direct3D11CaptureFramePool::CreateFreeThreaded(wrapped.as<wd::Direct3D11::IDirect3DDevice>(),wd::DirectXPixelFormat::B8G8R8A8UIntNormalized,2,item.Size());
        token=pool.FrameArrived([keep=signal](auto const&,auto const&){SetEvent(keep.get());});
        session=pool.CreateCaptureSession(item);session.IsCursorCaptureEnabled(false);
        auto display=session.as<wc::IDisplayGraphicsCaptureSession>();
        display.SetWindowExclusionList(winrt::single_threaded_vector<winrt::Windows::UI::WindowId>({{(uint64_t)(uintptr_t)hwnd}}));
        auto ids=display.GetWindowExclusionList();if(ids.Size()!=1||ids.GetAt(0).Value!=(uint64_t)(uintptr_t)hwnd)throw winrt::hresult_error(E_FAIL);
        // Only remove our session's border after the OS grants Borderless access.
        // A denial leaves the normal capture indicator intact.
        try{borderRequired=session.IsBorderRequired()?1:0;}
        catch(winrt::hresult_error const& e){borderError=int(e.code());}
        configureBorder();
        session.StartCapture();monitor=m;captureWidth=item.Size().Width;captureHeight=item.Size().Height;
    }
    void update(){
        if(!IsWindowVisible(hwnd)||IsIconic(hwnd)){if(pool)closeCapture();return;}
        RECT rect{};if(!GetClientRect(hwnd,&rect))winrt::throw_last_error();
        int w=rect.right,h=rect.bottom;if(w<=0||h<=0)return;
        if(w!=width||h!=height)resize(w,h);
        HMONITOR m=MonitorFromWindow(hwnd,MONITOR_DEFAULTTONEAREST);if(m!=monitor)capture(m);
        configureBorder(); // Permission may finish asynchronously after capture starts.
        auto frame=pool.TryGetNextFrame();if(!frame)return;
        while(auto newer=pool.TryGetNextFrame()){frame.Close();frame=std::move(newer);}
        const auto size=frame.ContentSize();
        if(size.Width<=0||size.Height<=0){frame.Close();return;}
        if(size.Width!=captureWidth||size.Height!=captureHeight){
            frame.Close();com_ptr<::IInspectable> wrapped;
            check_hresult(CreateDirect3D11DeviceFromDXGIDevice(device.as<IDXGIDevice>().get(),wrapped.put()));
            pool.Recreate(wrapped.as<wd::Direct3D11::IDirect3DDevice>(),wd::DirectXPixelFormat::B8G8R8A8UIntNormalized,2,size);
            captureWidth=size.Width;captureHeight=size.Height;return;
        }
        MONITORINFO info{sizeof(info)};if(GetMonitorInfoW(monitor,&info))monitorRect=info.rcMonitor;
        com_ptr<ID3D11Texture2D> screen;
        auto access=frame.Surface().as<::Windows::Graphics::DirectX::Direct3D11::IDirect3DDxgiInterfaceAccess>();
        check_hresult(access->GetInterface(__uuidof(ID3D11Texture2D),screen.put_void()));
        D3D11_TEXTURE2D_DESC desc{};screen->GetDesc(&desc);
        POINT origin{0,0};ClientToScreen(hwnd,&origin);
        int sx=origin.x-pad-monitorRect.left,sy=origin.y-pad-monitorRect.top;
        // Crop safely at monitor boundaries; do not retain old pixels outside the new source.
        D2D1_RECT_F crop{float(pad),float(pad),float(pad+w),float(pad+h)};
        int left=std::max(0,sx),top=std::max(0,sy),right=std::min(int(desc.Width),sx+w+2*pad),bottom=std::min(int(desc.Height),sy+h+2*pad);
        if(right<=left||bottom<=top){frame.Close();return;}
        com_ptr<ID3D11RenderTargetView> clear;check_hresult(device->CreateRenderTargetView(texture.get(),nullptr,clear.put()));
        const float neutral[]{.5f,.5f,.5f,1};context->ClearRenderTargetView(clear.get(),neutral);
        D3D11_BOX box{UINT(left),UINT(top),0,UINT(right),UINT(bottom),1};context->CopySubresourceRegion(texture.get(),0,left-sx,top-sy,0,screen.get(),0,&box);frame.Close();
        d2->BeginDraw();d2->Clear(D2D1::ColorF(0,0,0,0));D2D1_POINT_2F dest{0,0};
        d2->DrawImage(blur.get(),&dest,&crop,D2D1_INTERPOLATION_MODE_LINEAR,D2D1_COMPOSITE_MODE_SOURCE_COPY);
        check_hresult(d2->EndDraw());check_hresult(swap->Present(1,0));++frames;
        if(GetTickCount64()-sampled>=500){
            sampled=GetTickCount64();context->CopyResource(readback.get(),texture.get());D3D11_MAPPED_SUBRESOURCE mapped{};
            check_hresult(context->Map(readback.get(),0,D3D11_MAP_READ,0,&mapped));double sum=0;int n=0;
            auto linear=[](double c){c/=255;return c<=.04045?c/12.92:std::pow((c+.055)/1.055,2.4);};
            for(int y=pad;y<pad+h;y+=std::max(1,h/12))for(int x=pad;x<pad+w;x+=std::max(1,w/16)){
                auto p=(unsigned char*)mapped.pData+y*mapped.RowPitch+x*4;sum+=.2126*linear(p[2])+.7152*linear(p[1])+.0722*linear(p[0]);++n;
            }
            context->Unmap(readback.get(),0);luminance.store(sum/n);
        }
    }
};
void stop(){stopping=true;if(worker.joinable())worker.join();activeWindow=nullptr;luminance=-1;}
}
extern "C" int pd_glass_start(void* window){
    // Start consent on the caller's UI thread, without waiting for the prompt.
    // Never block the UI/capture lifecycle on user consent.
    int expected=-2;
    if(borderAccess.compare_exchange_strong(expected,-1))requestBorderless();
    std::lock_guard<std::mutex> lock(lifecycle);auto hwnd=static_cast<HWND>(window);
    if(activeWindow==hwnd&&worker.joinable()&&error.load()==0)return 0;
    stop();stopping=false;error=0;frames=0;
    std::promise<int> ready;auto future=ready.get_future();
    worker=std::thread([hwnd,ready=std::move(ready)]()mutable{
        bool signaled=false;
        try{
            winrt::init_apartment(winrt::apartment_type::multi_threaded);
            {Renderer renderer(hwnd);renderer.init();renderer.capture(MonitorFromWindow(hwnd,MONITOR_DEFAULTTONEAREST));renderer.update();ready.set_value(0);signaled=true;
                while(!stopping&&IsWindow(hwnd)){WaitForSingleObject(renderer.arrived,100);if(!stopping)renderer.update();}}
            winrt::uninit_apartment();
        }catch(winrt::hresult_error const& e){error=int(e.code());if(!signaled)ready.set_value(int(e.code()));}
        catch(...){error=E_FAIL;if(!signaled)ready.set_value(E_FAIL);}
    });
    int hr=future.get();if(hr<0)stop();else activeWindow=hwnd;return hr;
}
extern "C" void pd_glass_stop(){std::lock_guard<std::mutex> lock(lifecycle);stop();}
extern "C" double pd_glass_brightness(){return luminance.load();}
extern "C" int pd_glass_error(){return error.load();}
extern "C" unsigned long long pd_glass_frames(){return frames.load();}
extern "C" int pd_glass_border_access(){return borderAccess.load();}
extern "C" int pd_glass_border_required(){return borderRequired.load();}
extern "C" int pd_glass_border_error(){return borderError.load();}
