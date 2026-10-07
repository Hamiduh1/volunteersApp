(function () {
  "use strict";

  function prefersReducedMotion() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  function smoothBehavior() {
    return prefersReducedMotion() ? 'auto' : 'smooth';
  }

  /**
   * Apply .scrolled class to the body as the page is scrolled down
   */
  function toggleScrolled() {
    const selectBody = document.querySelector('body');
    const selectHeader = document.querySelector('#header');
    if (!selectBody || !selectHeader) return;
    if (!selectHeader.classList.contains('scroll-up-sticky') && !selectHeader.classList.contains('sticky-top') && !selectHeader.classList.contains('fixed-top')) return;
    window.scrollY > 100 ? selectBody.classList.add('scrolled') : selectBody.classList.remove('scrolled');
  }

  /**
   * Mobile nav toggle
   */
  const mobileNavToggleBtn = document.querySelector('.mobile-nav-toggle');
  const mobileNavToggleIcon = mobileNavToggleBtn ? mobileNavToggleBtn.querySelector('i') : null;
  const bodyEl = document.body;
  const navMenuEl = document.querySelector('#navmenu');

  function setMobileNavState(isOpen) {
    bodyEl.classList.toggle('mobile-nav-active', isOpen);
    if (navMenuEl) {
      navMenuEl.classList.toggle('navmenu-open', isOpen);
    }
    if (!mobileNavToggleBtn) return;
    const iconEl = mobileNavToggleIcon || mobileNavToggleBtn;
    iconEl.classList.toggle('bi-list', !isOpen);
    iconEl.classList.toggle('bi-x', isOpen);
    mobileNavToggleBtn.setAttribute('aria-expanded', String(isOpen));
  }

  function mobileNavToggle() {
    setMobileNavState(!bodyEl.classList.contains('mobile-nav-active'));
  }

  function closeMobileNav() {
    setMobileNavState(false);
  }

  if (mobileNavToggleBtn) {
    mobileNavToggleBtn.setAttribute('aria-expanded', 'false');
    mobileNavToggleBtn.addEventListener('click', mobileNavToggle);
    mobileNavToggleBtn.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        mobileNavToggle();
      }
    });
  }

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && bodyEl.classList.contains('mobile-nav-active')) {
      closeMobileNav();
    }
  });

  document.addEventListener('click', (event) => {
    if (!bodyEl.classList.contains('mobile-nav-active')) return;
    if (!navMenuEl || !mobileNavToggleBtn) return;
    if (navMenuEl.contains(event.target) || mobileNavToggleBtn.contains(event.target)) return;
    closeMobileNav();
  });

  /**
   * Page-level nav active state (set by data-nav-active on <body>)
   */
  function applyPageNavActive() {
    const navActive = document.body.getAttribute('data-nav-active');
    if (!navActive) return;

    const navLinkMap = {
      home: '/#hero',
      about: '/#about',
      imt: '/wallet/',
      services: '/#services',
      resources: '/blog.html',
      careers: '/careers.html',
      contact: '/contact/'
    };

    const targetHref = navLinkMap[navActive];
    if (!targetHref) return;

    document.querySelectorAll('#navmenu a').forEach((link) => {
      link.classList.remove('active');
      if (link.getAttribute('href') === targetHref) {
        link.classList.add('active');
      }
    });
  }

  /**
   * Hide mobile nav on same-page/hash links
   */
  document.querySelectorAll('#navmenu a').forEach(navmenu => {
    navmenu.addEventListener('click', () => {
      if (window.innerWidth < 1200 && bodyEl.classList.contains('mobile-nav-active')) {
        closeMobileNav();
      }
    });

  });

  window.addEventListener('resize', () => {
    if (window.innerWidth >= 1200 && bodyEl.classList.contains('mobile-nav-active')) {
      closeMobileNav();
    }
  });

  /**
   * Toggle mobile nav dropdowns
   */
  document.querySelectorAll('.navmenu .toggle-dropdown').forEach(navmenu => {
    navmenu.addEventListener('click', function (e) {
      e.preventDefault();
      this.parentNode.classList.toggle('active');
      this.parentNode.nextElementSibling.classList.toggle('dropdown-active');
      e.stopImmediatePropagation();
    });
  });

  /**
   * Scroll top button
   */
  let scrollTop = document.querySelector('.scroll-top');

  function toggleScrollTop() {
    if (scrollTop) {
      window.scrollY > 100 ? scrollTop.classList.add('active') : scrollTop.classList.remove('active');
    }
  }
  if (scrollTop) {
    scrollTop.addEventListener('click', (e) => {
      e.preventDefault();
      window.scrollTo({
        top: 0,
        behavior: smoothBehavior()
      });
    });
  }

  /**
   * Animation on scroll function and init
   */
  function aosInit() {
    if (typeof AOS === 'undefined') return;
    AOS.init({
      duration: 600,
      easing: 'ease-in-out',
      once: true,
      mirror: false
    });
  }
  /**
   * Initiate glightbox
   */
  if (typeof GLightbox !== 'undefined') {
    GLightbox({
      selector: '.glightbox'
    });
  }

  /**
   * Init swiper sliders
   */
  function initSwiper() {
    if (typeof Swiper === 'undefined') return;
    document.querySelectorAll(".init-swiper").forEach(function (swiperElement) {
      let config = JSON.parse(
        swiperElement.querySelector(".swiper-config").innerHTML.trim()
      );

      if (swiperElement.classList.contains("swiper-tab")) {
        initSwiperWithCustomPagination(swiperElement, config);
      } else {
        new Swiper(swiperElement, config);
      }
    });
  }

  /**
   * Initiate Pure Counter
   */
  if (typeof PureCounter !== 'undefined') {
    new PureCounter();
  }

  /**
   * Frequently Asked Questions Toggle
   */
  function initFaqAccordion() {
    document.querySelectorAll('.faq-item').forEach((faqItem) => {
      const trigger = faqItem.querySelector('.faq-trigger');
      if (!trigger) return;

      const isOpen = faqItem.classList.contains('faq-active');
      trigger.setAttribute('aria-expanded', String(isOpen));

      trigger.addEventListener('click', () => {
        const shouldOpen = !faqItem.classList.contains('faq-active');
        faqItem.classList.toggle('faq-active', shouldOpen);
        trigger.setAttribute('aria-expanded', String(shouldOpen));
      });
    });
  }

  /**
   * Contact form guardrails (honeypot + native validity)
   */
  function initContactForm() {
    const form = document.querySelector('#contact-form');
    if (!form) return;

    const honeypot = form.querySelector('input[name="company_site"]');
    const errorBox = form.querySelector('.error-message');

    form.addEventListener('submit', (event) => {
      if (honeypot && honeypot.value.trim() !== '') {
        event.preventDefault();
        if (errorBox) {
          errorBox.textContent = 'Submission blocked.';
          errorBox.style.display = 'block';
        }
        return;
      }

      if (!form.checkValidity()) {
        event.preventDefault();
        form.reportValidity();
      }
    });
  }

  /**
   * Intent-based prefetch for internal HTML pages
   */
  function normalizePrefetchTarget(rawHref) {
    if (!rawHref || rawHref.startsWith('#')) return null;
    if (/^(mailto:|tel:|javascript:|data:)/i.test(rawHref)) return null;

    let url;
    try {
      url = new URL(rawHref, window.location.href);
    } catch {
      return null;
    }

    if (url.origin !== window.location.origin) return null;
    if (!url.pathname.endsWith('.html')) return null;
    if (url.pathname === window.location.pathname) return null;

    return `${url.pathname}${url.search}`;
  }

  function prefetchDocument(href, prefetched) {
    if (!href || prefetched.has(href)) return;
    prefetched.add(href);

    const prefetchLink = document.createElement('link');
    prefetchLink.rel = 'prefetch';
    prefetchLink.href = href;
    prefetchLink.as = 'document';
    document.head.appendChild(prefetchLink);
  }

  function initLinkPrefetch() {
    const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
    if (connection?.saveData) return;

    const effectiveType = String(connection?.effectiveType || '').toLowerCase();
    if (effectiveType.includes('2g')) return;

    const probe = document.createElement('link');
    if (probe.relList && typeof probe.relList.supports === 'function' && !probe.relList.supports('prefetch')) {
      return;
    }

    const prefetched = new Set();
    document.querySelectorAll('a[href]').forEach((anchor) => {
      const hrefToPrefetch = normalizePrefetchTarget(anchor.getAttribute('href'));
      if (!hrefToPrefetch) return;

      const queuePrefetch = () => prefetchDocument(hrefToPrefetch, prefetched);
      const onIntent = () => {
        if ('requestIdleCallback' in window) {
          window.requestIdleCallback(queuePrefetch, { timeout: 1200 });
        } else {
          window.setTimeout(queuePrefetch, 120);
        }
      };

      anchor.addEventListener('pointerenter', onIntent, { once: true, passive: true });
      anchor.addEventListener('focus', onIntent, { once: true });
    });
  }

  /**
   * Correct scrolling position upon page load for URLs containing hash links.
   */
  window.addEventListener('load', function (e) {
    if (window.location.hash) {
      if (document.querySelector(window.location.hash)) {
        setTimeout(() => {
          let section = document.querySelector(window.location.hash);
          let scrollMarginTop = getComputedStyle(section).scrollMarginTop;
          window.scrollTo({
            top: section.offsetTop - parseInt(scrollMarginTop),
            behavior: smoothBehavior()
          });
        }, 100);
      }
    }
  });

  /**
   * Navmenu Scrollspy
   */
  let navmenulinks = document.querySelectorAll('.navmenu a');

  function navmenuScrollspy() {
    navmenulinks.forEach(navmenulink => {
      if (!navmenulink.hash) return;
      let section = document.querySelector(navmenulink.hash);
      if (!section) return;
      let position = window.scrollY + 200;
      if (position >= section.offsetTop && position <= (section.offsetTop + section.offsetHeight)) {
        document.querySelectorAll('.navmenu a.active').forEach(link => link.classList.remove('active'));
        navmenulink.classList.add('active');
      } else {
        navmenulink.classList.remove('active');
      }
    })
  }

  let scrollTicking = false;
  function handleScrollFrame() {
    toggleScrolled();
    toggleScrollTop();
    navmenuScrollspy();
    scrollTicking = false;
  }

  function onScroll() {
    if (scrollTicking) return;
    scrollTicking = true;
    window.requestAnimationFrame(handleScrollFrame);
  }

  window.addEventListener('load', function () {
    closeMobileNav();
    applyPageNavActive();
    toggleScrolled();
    toggleScrollTop();
    navmenuScrollspy();
    aosInit();
    initSwiper();
    initFaqAccordion();
    initContactForm();
    initLinkPrefetch();
  });

  document.addEventListener('scroll', onScroll, { passive: true });

})();
