// Iconos de lucide-static (ISC). Se importan como texto y se limpian: sin width/height/class, heredan tamano y color.
import scanBarcode from 'lucide-static/icons/scan-barcode.svg?raw';
import boxes from 'lucide-static/icons/boxes.svg?raw';
import receiptText from 'lucide-static/icons/receipt-text.svg?raw';
import chartColumn from 'lucide-static/icons/chart-column.svg?raw';
import truck from 'lucide-static/icons/truck.svg?raw';
import users from 'lucide-static/icons/users.svg?raw';
import store from 'lucide-static/icons/store.svg?raw';
import smartphone from 'lucide-static/icons/smartphone.svg?raw';
import sun from 'lucide-static/icons/sun.svg?raw';
import moon from 'lucide-static/icons/moon.svg?raw';
import shieldCheck from 'lucide-static/icons/shield-check.svg?raw';
import zap from 'lucide-static/icons/zap.svg?raw';
import camera from 'lucide-static/icons/camera.svg?raw';
import tags from 'lucide-static/icons/tags.svg?raw';
import rotateCcw from 'lucide-static/icons/rotate-ccw.svg?raw';
import check from 'lucide-static/icons/check.svg?raw';
import arrowRight from 'lucide-static/icons/arrow-right.svg?raw';
import plus from 'lucide-static/icons/plus.svg?raw';
import menu from 'lucide-static/icons/menu.svg?raw';
import x from 'lucide-static/icons/x.svg?raw';
import chevronDown from 'lucide-static/icons/chevron-down.svg?raw';
import bell from 'lucide-static/icons/bell.svg?raw';
import layers from 'lucide-static/icons/layers.svg?raw';
import badgeCheck from 'lucide-static/icons/badge-check.svg?raw';
import fileText from 'lucide-static/icons/file-text.svg?raw';
import mail from 'lucide-static/icons/mail.svg?raw';
import qrCode from 'lucide-static/icons/qr-code.svg?raw';
import packageCheck from 'lucide-static/icons/package-check.svg?raw';
import logIn from 'lucide-static/icons/log-in.svg?raw';
import userPlus from 'lucide-static/icons/user-plus.svg?raw';
import lock from 'lucide-static/icons/lock.svg?raw';
import banknote from 'lucide-static/icons/banknote.svg?raw';
import creditCard from 'lucide-static/icons/credit-card.svg?raw';
import arrowUpRight from 'lucide-static/icons/arrow-up-right.svg?raw';
import trendingUp from 'lucide-static/icons/trending-up.svg?raw';
import triangleAlert from 'lucide-static/icons/triangle-alert.svg?raw';
import clock from 'lucide-static/icons/clock.svg?raw';
import circleCheck from 'lucide-static/icons/circle-check.svg?raw';
import search from 'lucide-static/icons/search.svg?raw';
import refreshCw from 'lucide-static/icons/refresh-cw.svg?raw';
import download from 'lucide-static/icons/download.svg?raw';
import send from 'lucide-static/icons/send.svg?raw';
import wallet from 'lucide-static/icons/wallet.svg?raw';
import layoutGrid from 'lucide-static/icons/layout-grid.svg?raw';

const clean = (raw) => raw.replace(/<!--[sS]*?-->/g, '').replace(/s(width|height|class)="[^"]*"/g, '').trim();

export const icons = Object.fromEntries(Object.entries({
  'scan-barcode': scanBarcode,
  'boxes': boxes,
  'receipt-text': receiptText,
  'chart-column': chartColumn,
  'truck': truck,
  'users': users,
  'store': store,
  'smartphone': smartphone,
  'sun': sun,
  'moon': moon,
  'shield-check': shieldCheck,
  'zap': zap,
  'camera': camera,
  'tags': tags,
  'rotate-ccw': rotateCcw,
  'check': check,
  'arrow-right': arrowRight,
  'plus': plus,
  'menu': menu,
  'x': x,
  'chevron-down': chevronDown,
  'bell': bell,
  'layers': layers,
  'badge-check': badgeCheck,
  'file-text': fileText,
  'mail': mail,
  'qr-code': qrCode,
  'package-check': packageCheck,
  'log-in': logIn,
  'user-plus': userPlus,
  'lock': lock,
  'banknote': banknote,
  'credit-card': creditCard,
  'arrow-up-right': arrowUpRight,
  'trending-up': trendingUp,
  'triangle-alert': triangleAlert,
  'clock': clock,
  'circle-check': circleCheck,
  'search': search,
  'refresh-cw': refreshCw,
  'download': download,
  'send': send,
  'wallet': wallet,
  'layout-grid': layoutGrid,
}).map(([k, v]) => [k, clean(v)]));
